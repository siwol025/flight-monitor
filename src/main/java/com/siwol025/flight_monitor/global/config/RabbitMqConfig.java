package com.siwol025.flight_monitor.global.config;

import com.siwol025.flight_monitor.global.exception.custom.BadRequestException;
import java.time.Duration;
import java.util.Date;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.retry.RetryPolicy;

@Slf4j
@Configuration
public class RabbitMqConfig {

    public static final String NOTIFICATION_EXCHANGE = "notification";
    public static final String NOTIFICATION_DLX = "notification.dlx";
    public static final String PRICE_DROP_EVENTS_QUEUE = "price-drop-events";
    public static final String EMAIL_SEND_TASKS_QUEUE = "email-send-tasks";
    public static final String EMAIL_SEND_TASKS_DLQ = "email-send-tasks.dlq";

    @Value("${notification.email-task.concurrency:50}")
    private int emailTaskConcurrency;

    // 블로킹 재시도: 재시도 총 대기 = initial × (2^maxRetries − 1). 그동안 이 소비자는 다음 메시지를 받지 않는다.
    // 기본 5회(31초): 재시도 1주기가 장애 시간보다 짧으면 장애 중 소비자마다 메일을 DLQ로 소진시킨다.
    // 30초 SMTP 장애 측정에서 3회(7초)는 재시도 소진 150건, 5회는 0건
    @Value("${notification.email-task.retry.max-retries:5}")
    private int emailTaskMaxRetries;

    @Value("${notification.email-task.retry.initial-interval-ms:1000}")
    private long emailTaskInitialIntervalMs;

    // 블로킹 재시도 중에는 소비자에 미리 받아 둔 메시지도 기다리므로 1로 둔다(재전달 범위도 최소)
    @Value("${notification.email-task.prefetch:1}")
    private int emailTaskPrefetch;

    /**
     * 가격 하락 이벤트 큐. 팬아웃 리스너가 예외를 삼키므로 DLQ를 두지 않는다.
     */
    @Bean
    public Declarables priceDropEventTopology() {
        DirectExchange exchange = new DirectExchange(NOTIFICATION_EXCHANGE);
        Queue queue = QueueBuilder.durable(PRICE_DROP_EVENTS_QUEUE).build();
        return new Declarables(exchange, queue, BindingBuilder.bind(queue).to(exchange).with(PRICE_DROP_EVENTS_QUEUE));
    }

    /**
     * 메일 작업 큐와 DLQ. 재시도를 소진하거나 영구 오류로 reject된 메시지는 DLX를 거쳐 DLQ로 간다.
     * quorum queue는 기본 delivery-limit이 재시도와 겹쳐 classic durable 큐를 쓴다.
     */
    @Bean
    public Declarables emailSendTaskTopology() {
        DirectExchange exchange = new DirectExchange(NOTIFICATION_EXCHANGE);
        DirectExchange deadLetterExchange = new DirectExchange(NOTIFICATION_DLX);
        Queue queue = QueueBuilder.durable(EMAIL_SEND_TASKS_QUEUE)
                .deadLetterExchange(NOTIFICATION_DLX)
                .deadLetterRoutingKey(EMAIL_SEND_TASKS_DLQ)
                .build();
        Queue deadLetterQueue = QueueBuilder.durable(EMAIL_SEND_TASKS_DLQ).build();
        Binding binding = BindingBuilder.bind(queue).to(exchange).with(EMAIL_SEND_TASKS_QUEUE);
        Binding deadLetterBinding = BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(EMAIL_SEND_TASKS_DLQ);
        return new Declarables(exchange, deadLetterExchange, queue, deadLetterQueue, binding, deadLetterBinding);
    }

    @Bean
    public MessageConverter messageConverter() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter(
                "com.siwol025.flight_monitor.notification.dto",
                "com.siwol025.flight_monitor.subscription.dto");
        // 메일 리스너가 실패·DLQ·완료 로그를 같은 id로 잇는다
        converter.setCreateMessageIds(true);
        return converter;
    }

    // 메일 리스너의 지연 로그 기준(발행 시각). 발행할 때 붙인다
    @Bean
    public RabbitTemplateCustomizer publishTimestampCustomizer() {
        return template -> template.addBeforePublishPostProcessors(message -> {
            message.getMessageProperties().setTimestamp(new Date());
            return message;
        });
    }

    /**
     * 메일 발송 리스너 전용 팩토리. 리스너가 정상 종료해야 ack한다(AUTO).
     * 일시 오류는 블로킹 재시도 후, 영구 오류는 재시도 없이 reject(재큐 없음)되어 DLQ로 간다.
     */
    @Bean
    public SimpleRabbitListenerContainerFactory emailTaskListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setConcurrentConsumers(emailTaskConcurrency);
        factory.setPrefetchCount(emailTaskPrefetch);
        // 재시도 밖으로 새는 예외도 무한 재큐 대신 DLQ로 보낸다
        factory.setDefaultRequeueRejected(false);
        // 앞의 advice가 바깥이다: 재시도가 시도마다 안쪽의 실패 로그를 거친다
        factory.setAdviceChain(
                RetryInterceptorBuilder.stateless()
                        .retryPolicy(emailTaskRetryPolicy(emailTaskMaxRetries, emailTaskInitialIntervalMs))
                        .recoverer(new RejectAndDontRequeueRecoverer() {
                            @Override
                            public void recover(Message message, Throwable cause) {
                                log.error("[EmailTaskRetry] DLQ 이동: {} id={}", EMAIL_SEND_TASKS_QUEUE,
                                        message.getMessageProperties().getMessageId(), cause);
                                super.recover(message, cause);
                            }
                        })
                        .build(),
                emailTaskFailureLogInterceptor());
        return factory;
    }

    /**
     * 시도마다 실패를 남긴다. 재시도 interceptor에 리스너 훅이 없어 시도 번호 대신 줄 수로 센다.
     */
    private static MethodInterceptor emailTaskFailureLogInterceptor() {
        return invocation -> {
            try {
                return invocation.proceed();
            } catch (Throwable ex) {
                String messageId = invocation.getArguments()[1] instanceof Message message
                        ? message.getMessageProperties().getMessageId() : null;
                log.warn("[EmailTaskRetry] 발송 실패: {} id={} cause={}", EMAIL_SEND_TASKS_QUEUE, messageId,
                        NestedExceptionUtils.getMostSpecificCause(ex).getClass().getSimpleName());
                throw ex;
            }
        };
    }

    /**
     * 초기 간격부터 2배씩 maxRetries회 재시도한다(최초 시도 제외). 영구 오류(BadRequestException)는 원인 체인까지 보고 제외한다.
     */
    static RetryPolicy emailTaskRetryPolicy(int maxRetries, long initialIntervalMs) {
        return RetryPolicy.builder()
                .maxRetries(maxRetries)
                .delay(Duration.ofMillis(initialIntervalMs))
                .multiplier(2.0)
                .maxDelay(Duration.ofSeconds(30))
                .excludes(BadRequestException.class)
                .build();
    }
}
