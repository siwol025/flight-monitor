package com.siwol025.flight_monitor.fare;

import com.siwol025.flight_monitor.route.domain.RouteKey;
import java.util.Optional;

/**
 * 도메인이 외부 운임 업체에 바라는 것: 노선 → 최저가(없을 수 있음).
 */
public interface LowestFareFinder {

    Optional<LowestFare> findLowestFare(RouteKey routeKey);
}
