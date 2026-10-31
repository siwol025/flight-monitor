package com.siwol025.flight_monitor.fare;

/**
 * 주문 금액·통화가 업체의 현재 가격과 다름. 업체 장애가 아니라 정상적인 업무 결과라서
 * {@link FareProviderException}의 하위로 두지 않는다.
 */
public class FlightOfferPriceChangedException extends RuntimeException {

    public FlightOfferPriceChangedException(String message) {
        super(message);
    }
}
