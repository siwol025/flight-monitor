package com.siwol025.flight_monitor.booking.dto.response;

import com.siwol025.flight_monitor.fare.FlightOffer;
import com.siwol025.flight_monitor.route.domain.SeatGrade;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 예매할 수 있는 항공권 1건. 시각은 업체가 준 현지 시각 그대로다.
 */
public record FlightOfferResponse(
        String offerId,
        BigDecimal amount,
        String currency,
        String departureAirport,
        String arrivalAirport,
        SeatGrade seatGrade,
        String carrierCode,
        String carrierName,
        String flightNumber,
        LocalDateTime departingAt,
        LocalDateTime arrivingAt
) {
    public static FlightOfferResponse from(FlightOffer offer) {
        return new FlightOfferResponse(offer.offerId(), offer.amount(), offer.currency(), offer.departureAirport(),
                offer.arrivalAirport(), offer.seatGrade(), offer.carrierCode(), offer.carrierName(),
                offer.flightNumber(), offer.departingAt(), offer.arrivingAt());
    }
}
