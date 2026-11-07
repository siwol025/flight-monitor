package com.siwol025.flight_monitor.payment.adapter.toss;

/**
 * POST /v1/payments/confirm 본문.
 */
record TossConfirmRequest(String paymentKey, String orderId, long amount) {
}
