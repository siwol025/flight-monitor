package com.siwol025.flight_monitor.payment;

import java.time.Instant;

/**
 * 결제사가 알려 준 결제의 현재 상태.
 *
 * @param totalAmount 결제 총액(원). 승인 전에도 채워져 있으므로 승인 판정에는 status만 쓴다
 * @param approvedAt  승인 시각. 승인 전이면 null
 */
public record PaymentSnapshot(
        String paymentKey,
        String orderId,
        PaymentStatus status,
        long totalAmount,
        Instant approvedAt
) {
}
