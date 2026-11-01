package com.siwol025.flight_monitor.fare;

/**
 * 주문하려는 항공권을 더는 예매할 수 없음(매진·없는 offer). 업체 장애가 아니라 정상적인 업무 결과라서
 * {@link FareProviderException}의 하위로 두지 않는다.
 */
public class FlightOfferUnavailableException extends RuntimeException {

    public FlightOfferUnavailableException(String message) {
        super(message);
    }
}
