package com.siwol025.flight_monitor.fare.adapter.duffel;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * POST /air/orders hold 요청 본문. hold는 payments를 넣으면 거절되므로 필드가 없다. 날짜는 Duffel처럼 문자열로 보낸다.
 */
record HoldOrderRequest(Data data) {

    record Data(String type,
                @JsonProperty("selected_offers") List<String> selectedOffers,
                List<Passenger> passengers,
                Map<String, String> metadata) {
    }

    record Passenger(String id,
                     String title,
                     @JsonProperty("given_name") String givenName,
                     @JsonProperty("family_name") String familyName,
                     String gender,
                     @JsonProperty("born_on") String bornOn,
                     String email,
                     @JsonProperty("phone_number") String phoneNumber) {
    }
}
