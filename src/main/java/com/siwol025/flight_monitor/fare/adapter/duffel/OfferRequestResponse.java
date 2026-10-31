package com.siwol025.flight_monitor.fare.adapter.duffel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * POST /air/offer_requests 응답 중 최저가 선택·항공권 목록에 쓰는 필드만. 실제 응답에는 쓰지 않는 필드가 많으므로
 * 모든 단계에서 모르는 필드를 무시한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record OfferRequestResponse(Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Data(List<Offer> offers) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Offer(String id,
                 @JsonProperty("total_amount") BigDecimal totalAmount,
                 @JsonProperty("total_currency") String totalCurrency,
                 List<Slice> slices) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Slice(List<Segment> segments) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Segment(Place origin,
                   Place destination,
                   @JsonProperty("marketing_carrier") Carrier marketingCarrier,
                   @JsonProperty("marketing_carrier_flight_number") String marketingCarrierFlightNumber,
                   @JsonProperty("departing_at") LocalDateTime departingAt,
                   @JsonProperty("arriving_at") LocalDateTime arrivingAt,
                   List<SegmentPassenger> passengers) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Place(@JsonProperty("iata_code") String iataCode) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SegmentPassenger(@JsonProperty("cabin_class") String cabinClass) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Carrier(@JsonProperty("iata_code") String iataCode, String name) {
    }
}
