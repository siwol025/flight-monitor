package com.siwol025.flight_monitor.notification.producer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;

import com.siwol025.flight_monitor.notification.dto.PriceDropNotificationDto;
import java.net.ConnectException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class NotificationProducerTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private NotificationProducer producer;

    @Test
    void publishPriceDrop_발행실패해도_예외전파안됨() {
        willThrow(new AmqpConnectException(new ConnectException("브로커 연결 실패")))
                .given(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        // 발행 실패가 감시 흐름(가격 저장)으로 번지지 않는다. 실패는 로그로만 남는다
        assertDoesNotThrow(() -> producer.publishPriceDrop(PriceDropNotificationDto.builder().routeId(1L).build()));
    }
}
