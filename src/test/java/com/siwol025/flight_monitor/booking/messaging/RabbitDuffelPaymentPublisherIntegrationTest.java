package com.siwol025.flight_monitor.booking.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.siwol025.flight_monitor.global.config.RabbitMqConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Binding.DestinationType;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 결제 명령 발행 결과(publisher confirm·mandatory return)를 실제 브로커 응답대로 판정하는지 본다.
 * 소비자는 띄우지 않아 발행된 메시지가 큐에 남는다.
 */
@SpringBootTest(classes = {RabbitMqConfig.class, BookingMessagingConfig.class, RabbitDuffelPaymentPublisher.class})
@ImportAutoConfiguration(RabbitAutoConfiguration.class)
@Testcontainers
class RabbitDuffelPaymentPublisherIntegrationTest {

    @Container
    private static final RabbitMQContainer RABBIT =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:4.3.7-management"));

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }

    @Autowired
    private RabbitDuffelPaymentPublisher publisher;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @AfterEach
    void redeclareTopology() {
        ((RabbitAdmin) amqpAdmin).initialize();
        amqpAdmin.purgeQueue(BookingMessagingConfig.DUFFEL_PAYMENT_QUEUE);
    }

    @Test
    void publish_바인딩없으면_실패로감지() {
        // 운영 사고 재현: exchange·큐는 있으나 바인딩이 없어 브로커가 ack하면서 메시지를 버린다
        amqpAdmin.removeBinding(new Binding(BookingMessagingConfig.DUFFEL_PAYMENT_QUEUE, DestinationType.QUEUE,
                BookingMessagingConfig.BOOKING_EXCHANGE, BookingMessagingConfig.DUFFEL_PAYMENT_QUEUE, null));

        assertThatThrownBy(() -> publisher.publish(30L))
                .isInstanceOf(DuffelPaymentPublishException.class)
                .hasMessageContaining("라우팅 실패");
        assertThat(amqpAdmin.getQueueInfo(BookingMessagingConfig.DUFFEL_PAYMENT_QUEUE).getMessageCount()).isZero();
    }

    @Test
    void publish_정상바인딩_확인후성공() {
        publisher.publish(30L);

        assertThat(amqpAdmin.getQueueInfo(BookingMessagingConfig.DUFFEL_PAYMENT_QUEUE).getMessageCount()).isEqualTo(1);
    }
}
