package com.siwol025.flight_monitor.booking.messaging;

/**
 * Toss 승인이 커밋된 예매의 Duffel hold 결제(⑥)를 요청한다. 상태는 소비자가 DB에서 읽으므로 예매 ID만 보낸다.
 * 발행이 실패해도 승인은 이미 커밋됐다: 멈춘 PAYMENT_APPROVED는 확인 스케줄러가 다시 발행한다.
 */
public interface DuffelPaymentPublisher {

    void publish(long bookingId);
}
