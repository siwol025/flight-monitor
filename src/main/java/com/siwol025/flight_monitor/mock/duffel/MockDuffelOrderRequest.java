package com.siwol025.flight_monitor.mock.duffel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Duffel POST /air/orders 요청 본문 중 mock이 쓰는 부분(첫 offer, 첫 결제, 승객). 형식 검증은 하지 않는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MockDuffelOrderRequest(Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(String type,
                       @JsonProperty("selected_offers") List<String> selectedOffers,
                       List<Payment> payments,
                       List<Passenger> passengers) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payment(String type, String amount, String currency) {
    }

    // 응답에 그대로 돌려준다
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Passenger(String id, String title, String gender,
                            @JsonProperty("given_name") String givenName,
                            @JsonProperty("family_name") String familyName,
                            @JsonProperty("born_on") String bornOn,
                            String email,
                            @JsonProperty("phone_number") String phoneNumber) {
    }
}
