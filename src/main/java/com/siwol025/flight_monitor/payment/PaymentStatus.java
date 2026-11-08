package com.siwol025.flight_monitor.payment;

/**
 * 결제사의 결제 상태. 승인된 것은 DONE뿐이다.
 */
public enum PaymentStatus {
    READY,
    /** 인증은 끝났고 승인 전이다. 아직 청구되지 않았다 */
    IN_PROGRESS,
    WAITING_FOR_DEPOSIT,
    DONE,
    CANCELED,
    PARTIAL_CANCELED,
    /** 승인 실패 */
    ABORTED,
    /** 승인 기한(인증 후 10분)이 지났다 */
    EXPIRED
}
