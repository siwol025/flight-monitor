package com.siwol025.flight_monitor.fare.adapter.duffel;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * POST /air/offer_requests 요청 본문(Duffel 형식의 부분집합: 편도 1구간, 성인 1명).
 */
record OfferRequest(Data data) {

    record Data(List<Slice> slices, List<Passenger> passengers, @JsonProperty("cabin_class") String cabinClass) {
    }

    record Slice(String origin, String destination, @JsonProperty("departure_date") String departureDate) {
    }

    record Passenger(String type) {
    }
}
