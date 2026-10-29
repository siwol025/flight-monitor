package com.siwol025.flight_monitor.mock.duffel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;

/**
 * Duffel POST /air/offer_requests 요청 본문 중 mock이 쓰는 부분(첫 구간, 좌석등급). passengers 등은 무시한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MockDuffelOfferRequest(Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(List<Slice> slices, @JsonProperty("cabin_class") String cabinClass) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Slice(String origin, String destination, @JsonProperty("departure_date") LocalDate departureDate) {
    }
}
