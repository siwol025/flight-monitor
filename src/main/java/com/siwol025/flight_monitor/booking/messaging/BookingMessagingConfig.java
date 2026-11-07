package com.siwol025.flight_monitor.booking.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;

/**
 * Duffel hold 결제(⑥) 큐. 발행은 승인 API가 도는 모든 인스턴스에서 하므로 토폴로지·팩토리는 프로필 조건이 없고, 소비자
 * ({@link DuffelPaymentListener})만 mock 인스턴스에서 빠진다.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(BookingConsumerProperties.class)
public class BookingMessagingConfig {

    public static final String BOOKING_EXCHANGE = "booking";
    public static final String BOOKING_DLX = "booking.dlx";
    public static final String DUFFEL_PAYMENT_QUEUE = "booking.duffel-payment";
    public static final String DUFFEL_PAYMENT_DLQ = "booking.duffel-payment.dlq";

    private final BookingConsumerProperties properties;

    public BookingMessagingConfig(BookingConsumerProperties properties) {
        this.properties = properties;
    }

    /** 결제 큐와 DLQ. 재시도를 소진한 메시지는 DLX를 거쳐 DLQ로 간다(재시도와 겹치지 않게 classic durable) */
    @Bean
    public Declarables bookingTopology() {
        DirectExchange exchange = new DirectExchange(BOOKING_EXCHANGE);
        DirectExchange deadLetterExchange = new DirectExchange(BOOKING_DLX);
        Queue queue = QueueBuilder.durable(DUFFEL_PAYMENT_QUEUE)
                .deadLetterExchange(BOOKING_DLX)
                .deadLetterRoutingKey(DUFFEL_PAYMENT_DLQ)
                .build();
        Queue deadLetterQueue = QueueBuilder.durable(DUFFEL_PAYMENT_DLQ).build();
        return new Declarables(exchange, deadLetterExchange, queue, deadLetterQueue,
                BindingBuilder.bind(queue).to(exchange).with(DUFFEL_PAYMENT_QUEUE),
                BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(DUFFEL_PAYMENT_DLQ));
    }

    /**
     * 결제 리스너 전용 팩토리. 리스너가 정상 종료해야 ack한다(AUTO). 리스너는 결제가 만들어지지 않았다고 확실할 때만 예외를
     * 던지므로, 재시도는 결제를 다시 만들 수 없는 경우에만 일어난다. 재시도를 소진하면 재큐 없이 DLQ로 간다.
     */
    @Bean
    public SimpleRabbitListenerContainerFactory duffelPaymentListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setConcurrentConsumers(properties.getConcurrency());
        factory.setPrefetchCount(properties.getPrefetch());
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(
                RetryInterceptorBuilder.stateless()
                        .retryPolicy(RetryPolicy.builder()
                                .maxRetries(properties.getMaxRetries())
                                .delay(properties.getInitialInterval())
                                .multiplier(2.0)
                                .build())
                        .recoverer(new RejectAndDontRequeueRecoverer() {
                            @Override
                            public void recover(Message message, Throwable cause) {
                                log.error("[DuffelPaymentRetry] DLQ 이동: {} id={}", DUFFEL_PAYMENT_QUEUE,
                                        message.getMessageProperties().getMessageId(), cause);
                                super.recover(message, cause);
                            }
                        })
                        .build());
        return factory;
    }
}
