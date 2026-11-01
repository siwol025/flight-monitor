package com.siwol025.flight_monitor.fare;

import com.siwol025.flight_monitor.route.domain.SeatGrade;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 예매할 수 있는 항공권 1건. 출발 공항·항공사·편명·출발은 첫 구간, 도착 공항·도착은 마지막 구간이다.
 * 시각은 업체가 준 현지 시각 그대로다(오프셋 없음).
 */
public record FlightOffer(
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
}
