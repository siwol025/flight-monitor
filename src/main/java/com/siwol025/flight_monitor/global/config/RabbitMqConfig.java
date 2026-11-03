package com.siwol025.flight_monitor.global.config;

import com.siwol025.flight_monitor.global.exception.custom.BadRequestException;
import java.time.Duration;
import java.util.Date;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.core.retry.RetryPolicy;

@Slf4j
@Configuration
public class RabbitMqConfig {

    public static final String NOTIFICATION_EXCHANGE = "notification";
    public static final String NOTIFICATION_DLX = "notification.dlx";
    public static final String PRICE_DROP_EVENTS_QUEUE = "price-drop-events";
    public static final String PRICE_DROP_EVENTS_DLQ = "price-drop-events.dlq";
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

    // 팬아웃 블로킹 재시도. 실패 원인은 주로 구독자 조회(DB)라 메일(SMTP)과 따로 조정한다. 기본 5회(31초)
    @Value("${notification.price-drop-fanout.retry.max-retries:5}")
    private int priceDropFanoutMaxRetries;

    @Value("${notification.price-drop-fanout.retry.initial-interval-ms:1000}")
    private long priceDropFanoutInitialIntervalMs;

    /**
     * 가격 하락 이벤트 큐와 DLQ. 팬아웃이 재시도를 소진하면 DLX를 거쳐 DLQ로 간다.
     * 기존 브로커에 DLX 인자 없이 선언된 큐가 있으면 인자가 달라 선언이 실패하므로, 배포 전에 빈 큐를 지우고 다시 선언한다.
     */
    @Bean
    public Declarables priceDropEventTopology() {
        DirectExchange exchange = new DirectExchange(NOTIFICATION_EXCHANGE);
        DirectExchange deadLetterExchange = new DirectExchange(NOTIFICATION_DLX);
        Queue queue = QueueBuilder.durable(PRICE_DROP_EVENTS_QUEUE)
                .deadLetterExchange(NOTIFICATION_DLX)
                .deadLetterRoutingKey(PRICE_DROP_EVENTS_DLQ)
                .build();
        Queue deadLetterQueue = QueueBuilder.durable(PRICE_DROP_EVENTS_DLQ).build();
        Binding binding = BindingBuilder.bind(queue).to(exchange).with(PRICE_DROP_EVENTS_QUEUE);
        Binding deadLetterBinding = BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(PRICE_DROP_EVENTS_DLQ);
        return new Declarables(exchange, deadLetterExchange, queue, deadLetterQueue, binding, deadLetterBinding);
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
        factory.setAdviceChain(
                RetryInterceptorBuilder.stateless()
                        .retryPolicy(retryPolicy(emailTaskMaxRetries, emailTaskInitialIntervalMs))
                        .recoverer(new RejectAndDontRequeueRecoverer() {
                            @Override
                            public void recover(Message message, Throwable cause) {
                                log.error("[EmailTaskRetry] DLQ 이동: {} id={}", EMAIL_SEND_TASKS_QUEUE,
                                        message.getMessageProperties().getMessageId(), cause);
                                super.recover(message, cause);
                            }
                        })
                        .build());
        return factory;
    }

    /**
     * 가격 하락 팬아웃 리스너 전용 팩토리. 리스너가 정상 종료해야 ack한다(AUTO).
     * 예외는 블로킹 재시도 후 reject(재큐 없음)되어 DLQ로 간다. 재시도 중에는 미리 받아 둔 메시지도 기다리므로 prefetch는 1이다.
     */
    @Bean
    public SimpleRabbitListenerContainerFactory priceDropFanoutListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setConcurrentConsumers(3);
        factory.setPrefetchCount(1);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(
                RetryInterceptorBuilder.stateless()
                        .retryPolicy(retryPolicy(priceDropFanoutMaxRetries, priceDropFanoutInitialIntervalMs))
                        .recoverer(new RejectAndDontRequeueRecoverer() {
                            @Override
                            public void recover(Message message, Throwable cause) {
                                log.error("[PriceDropFanoutRetry] DLQ 이동: {} id={}", PRICE_DROP_EVENTS_QUEUE,
                                        message.getMessageProperties().getMessageId(), cause);
                                super.recover(message, cause);
                            }
                        })
                        .build());
        return factory;
    }

    /**
     * 초기 간격부터 2배씩 maxRetries회 재시도한다(최초 시도 제외). 영구 오류(BadRequestException)는 원인 체인까지 보고 제외한다.
     */
    static RetryPolicy retryPolicy(int maxRetries, long initialIntervalMs) {
        return RetryPolicy.builder()
                .maxRetries(maxRetries)
                .delay(Duration.ofMillis(initialIntervalMs))
                .multiplier(2.0)
                .maxDelay(Duration.ofSeconds(30))
                .excludes(BadRequestException.class)
                .build();
    }
}
