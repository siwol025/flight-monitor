package com.siwol025.flight_monitor.fare;

/**
 * hold 주문 요청. passengerId는 offer가 준 업체 승객 ID({@link OfferTerms#passengerIds()}), bookingId는 업체 주문에
 * 남겨 역조회에 쓰는 우리 예매 ID다.
 */
public record HoldOrderCommand(
        String offerId,
        String passengerId,
        Passenger passenger,
        String bookingId
) {
}
