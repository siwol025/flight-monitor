package com.siwol025.flight_monitor.route.domain;

/**
 * 노선 상태. 업체가 노선 자체를 거부하면(422) 무효로 돌려 감시에서 뺀다.
 */
public enum RouteStatus {
    ACTIVE,
    INVALID
}
