package com.siwol025.flight_monitor.payment;

/**
 * 결제사 승인·조회 포트. 구현은 재요청하지 않는다: 결과가 불명확하면 {@link PaymentFailureKind#UNKNOWN}으로 알리고,
 * 다시 보낼지는 조회 결과를 본 호출한 쪽이 정한다.
 */
public interface PaymentGateway {

    /** 결제 승인. 같은 idempotencyKey로 다시 보내면 결제사는 첫 응답(실패 포함)을 그대로 돌려준다 */
    PaymentSnapshot confirm(String paymentKey, String orderId, long amount, String idempotencyKey);

    /** 결제 조회(읽기) */
    PaymentSnapshot find(String paymentKey);
}
