package com.siwol025.flight_monitor.fare.adapter.duffel;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * POST /air/orders 요청 본문(Duffel 주문 생성 형식의 부분집합). 금액·날짜는 Duffel처럼 문자열로 보낸다.
 */
record OrderRequest(Data data) {

    record Data(String type,
                @JsonProperty("selected_offers") List<String> selectedOffers,
                List<Payment> payments,
                List<Passenger> passengers) {
    }

    record Payment(String type, String amount, String currency) {
    }

    record Passenger(String title,
                     @JsonProperty("given_name") String givenName,
                     @JsonProperty("family_name") String familyName,
                     String gender,
                     @JsonProperty("born_on") String bornOn,
                     String email,
                     @JsonProperty("phone_number") String phoneNumber) {
    }
}
