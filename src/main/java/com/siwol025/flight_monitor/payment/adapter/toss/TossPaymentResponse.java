package com.siwol025.flight_monitor.payment.adapter.toss;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;

/**
 * Payment 객체 중 승인 판정에 쓰는 필드만. secret·card 등 나머지는 읽지 않는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record TossPaymentResponse(String paymentKey, String orderId, String status, long totalAmount,
                           OffsetDateTime approvedAt) {
}
