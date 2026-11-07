package com.siwol025.flight_monitor.booking.idempotency;

/** 멱등키를 쓰는 우리 API. 같은 키라도 API가 다르면 다른 요청이다 */
public enum IdempotencyScope {
    BOOKING_CREATE,
    PAYMENT_CONFIRM
}
