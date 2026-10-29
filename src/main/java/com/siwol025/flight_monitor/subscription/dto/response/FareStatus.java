package com.siwol025.flight_monitor.subscription.dto.response;

import com.siwol025.flight_monitor.route.domain.Route;
import com.siwol025.flight_monitor.route.domain.RouteLatestFare;
import com.siwol025.flight_monitor.route.domain.RouteStatus;

/**
 * 구독 조회에 보여 주는 노선 최저가 상태.
 */
public enum FareStatus {
    AWAITING_MEASUREMENT, // 측정 대기: 노선의 첫 최저가를 아직 관측하지 못함(다음 감시 주기에 채워진다)
    MEASURED,
    ROUTE_INVALID;        // 노선 무효: 업체가 노선 자체를 거부해(422) 감시에서 빠짐

    // 무효 노선은 감시가 멈췄으므로 남아 있는 최저가보다 무효 상태를 먼저 보여 준다
    static FareStatus of(Route route, RouteLatestFare latestFare) {
        if (route.getStatus() == RouteStatus.INVALID) {
            return ROUTE_INVALID;
        }
        return latestFare == null ? AWAITING_MEASUREMENT : MEASURED;
    }
}
