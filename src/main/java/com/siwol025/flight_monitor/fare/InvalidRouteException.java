package com.siwol025.flight_monitor.fare;

/**
 * 업체가 노선 자체를 거부함(없는 공항 코드 등). 다시 시도해도 같으므로 노선을 무효로 돌린다.
 */
public class InvalidRouteException extends FareProviderException {

    public InvalidRouteException(String message) {
        super(message);
    }
}
