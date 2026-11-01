package com.siwol025.flight_monitor.fare.adapter.duffel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/**
 * POST /air/orders 응답 중 주문 확정에 쓰는 필드만.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record OrderResponse(Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Data(String id,
                @JsonProperty("booking_reference") String bookingReference,
                @JsonProperty("total_amount") BigDecimal totalAmount,
                @JsonProperty("total_currency") String totalCurrency) {
    }
}
