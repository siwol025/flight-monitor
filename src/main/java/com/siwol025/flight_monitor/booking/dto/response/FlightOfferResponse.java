package com.siwol025.flight_monitor.booking.dto.response;

import com.siwol025.flight_monitor.fare.FlightOffer;
import com.siwol025.flight_monitor.route.domain.SeatGrade;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * 항공권 1건. 시각은 업체가 준 현지 시각 그대로다. bookable이 false면 우리 흐름으로 예매할 수 없다(허용 외 항공사,
 * 결제를 미룰 수 없는 offer 등). estimatedChargeAmount는 고정 환율로 계산한 원화 예상 결제 금액(환율을 모르는 통화면 null),
 * expiresAt은 offer 만료 시각이다.
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
        LocalDateTime arrivingAt,
        boolean bookable,
        Instant expiresAt,
        Long estimatedChargeAmount
) {
    public static FlightOfferResponse of(FlightOffer offer, boolean bookable, Long estimatedChargeAmount) {
        return new FlightOfferResponse(offer.offerId(), offer.amount(), offer.currency(), offer.departureAirport(),
                offer.arrivalAirport(), offer.seatGrade(), offer.carrierCode(), offer.carrierName(),
                offer.flightNumber(), offer.departingAt(), offer.arrivingAt(), bookable, offer.terms().expiresAt(),
                estimatedChargeAmount);
    }
}
