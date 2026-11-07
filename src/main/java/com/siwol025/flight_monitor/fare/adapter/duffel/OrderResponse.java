package com.siwol025.flight_monitor.fare.adapter.duffel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * POST /air/orders·GET /air/orders/{id} 응답 중 주문 확정·결제·발권 확인에 쓰는 필드만.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record OrderResponse(Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Data(String id,
                @JsonProperty("offer_id") String offerId,
                @JsonProperty("booking_reference") String bookingReference,
                @JsonProperty("total_amount") BigDecimal totalAmount,
                @JsonProperty("total_currency") String totalCurrency,
                @JsonProperty("payment_status") PaymentStatus paymentStatus,
                List<Document> documents,
                Map<String, String> metadata) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PaymentStatus(@JsonProperty("payment_required_by") Instant paymentRequiredBy,
                         @JsonProperty("paid_at") Instant paidAt) {
    }

    /** 발권 문서. electronic_ticket의 unique_identifier가 항공권 번호다 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Document(String type, @JsonProperty("unique_identifier") String uniqueIdentifier) {
    }
}
