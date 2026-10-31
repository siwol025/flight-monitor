package com.siwol025.flight_monitor.fare;

import java.math.BigDecimal;

/**
 * 예매 흐름이 외부 운임 업체에 바라는 것: offer를 이 금액·통화로 승객 1명에게 주문한다.
 * 매진·없는 offer면 {@link FlightOfferUnavailableException}, 금액·통화가 현재 가격과 다르면
 * {@link FlightOfferPriceChangedException}이고, 업체 장애·한도 초과는 {@link FareProviderException}의 하위 예외다.
 */
public interface FlightOrderPlacer {

    PlacedOrder placeOrder(String offerId, BigDecimal amount, String currency, Passenger passenger);
}
