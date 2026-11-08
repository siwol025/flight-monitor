package com.siwol025.flight_monitor.notification.producer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;

import com.siwol025.flight_monitor.notification.dto.PriceDropNotificationDto;
import java.net.ConnectException;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class NotificationProducerTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Test
    void publishPriceDrop_전송실패면_예외대신_실패결과() {
        willThrow(new AmqpConnectException(new ConnectException("브로커 연결 실패")))
                .given(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class), any(CorrelationData.class));
        NotificationProducer producer = new NotificationProducer(rabbitTemplate, Runnable::run, Duration.ofSeconds(10));

        // 발행 실패가 감시 흐름으로 예외로 번지지 않고, 호출자가 최신가를 전진시키지 않도록 실패 결과로 알린다
        CompletionStage<Void> result = producer.publishPriceDrop(PriceDropNotificationDto.builder().routeId(1L).build());

        assertThat(result.toCompletableFuture()).failsWithin(Duration.ZERO)
                .withThrowableOfType(ExecutionException.class)
                .withCauseInstanceOf(PriceDropPublishException.class);
    }
}
