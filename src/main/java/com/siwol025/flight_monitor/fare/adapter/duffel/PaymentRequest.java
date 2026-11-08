package com.siwol025.flight_monitor.fare.adapter.duffel;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POST /air/payments 요청 본문. hold 주문을 잔액(balance)으로 결제한다. 금액은 Duffel처럼 문자열로 보낸다.
 */
record PaymentRequest(Data data) {

    record Data(@JsonProperty("order_id") String orderId, Payment payment) {
    }

    record Payment(String type, String amount, String currency) {
    }
}
