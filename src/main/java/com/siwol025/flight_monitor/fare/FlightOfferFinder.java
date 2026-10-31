package com.siwol025.flight_monitor.fare;

import com.siwol025.flight_monitor.route.domain.RouteKey;
import java.util.List;
import java.util.Optional;

/**
 * 예매 흐름이 외부 운임 업체에 바라는 것: 노선 → 예매 가능한 항공권 목록(가격 오름차순),
 * offer ID → 지금 예매할 수 있는 그 항공권(매진·없음이면 빈 값).
 */
public interface FlightOfferFinder {

    List<FlightOffer> searchOffers(RouteKey routeKey);

    Optional<FlightOffer> findOffer(String offerId);
}
