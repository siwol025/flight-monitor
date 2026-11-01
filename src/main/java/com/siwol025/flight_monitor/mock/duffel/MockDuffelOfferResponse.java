package com.siwol025.flight_monitor.mock.duffel;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Duffel 오퍼 검색 응답의 부분집합(어댑터가 읽는 필드 + 구간 출발·도착 공항). 필드명과 값 형식은 실제 응답과 같다.
 */
public record MockDuffelOfferResponse(Data data) {

    public record Data(List<Offer> offers) {
    }

    public record Offer(String id,
                        @JsonProperty("total_amount") String totalAmount,
                        @JsonProperty("total_currency") String totalCurrency,
                        List<Slice> slices) {
    }

    public record Slice(List<Segment> segments) {
    }

    public record Segment(Place origin,
                          Place destination,
                          @JsonProperty("marketing_carrier") Carrier marketingCarrier,
                          @JsonProperty("marketing_carrier_flight_number") String marketingCarrierFlightNumber,
                          @JsonProperty("departing_at") String departingAt,
                          @JsonProperty("arriving_at") String arrivingAt,
                          List<SegmentPassenger> passengers) {
    }

    // 실제 Duffel처럼 구간의 승객마다 좌석 등급을 둔다(mock은 성인 1명)
    public record SegmentPassenger(@JsonProperty("cabin_class") String cabinClass) {
    }

    public record Place(@JsonProperty("iata_code") String iataCode) {
    }

    public record Carrier(@JsonProperty("iata_code") String iataCode, String name) {
    }
}
