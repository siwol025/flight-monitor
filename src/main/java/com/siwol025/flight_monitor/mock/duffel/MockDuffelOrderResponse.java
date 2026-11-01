package com.siwol025.flight_monitor.mock.duffel;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Duffel 주문 생성 응답의 부분집합. slices는 검색 offer와 같은 모양이다.
 */
public record MockDuffelOrderResponse(Data data) {

    public record Data(String id,
                       @JsonProperty("booking_reference") String bookingReference,
                       @JsonProperty("total_amount") String totalAmount,
                       @JsonProperty("total_currency") String totalCurrency,
                       List<MockDuffelOrderRequest.Passenger> passengers,
                       List<MockDuffelOfferResponse.Slice> slices) {
    }
}
