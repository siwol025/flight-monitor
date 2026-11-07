package com.siwol025.flight_monitor.booking.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 결제 큐 발행기. 승인 API는 프로필 조건 없이 모든 인스턴스에서 돌므로 이 빈도 프로필 조건을 두지 않는다
 * (빠지면 승인한 예매가 발행 없이 스케줄러 재발행만 기다린다). 실패는 호출한 쪽으로 던진다.
 */
@Component
@RequiredArgsConstructor
public class RabbitDuffelPaymentPublisher implements DuffelPaymentPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publish(long bookingId) {
        rabbitTemplate.convertAndSend(BookingMessagingConfig.BOOKING_EXCHANGE,
                BookingMessagingConfig.DUFFEL_PAYMENT_QUEUE, new BookingCommand(bookingId));
    }
}
