package com.siwol025.flight_monitor.mock.flight.dto.response;

import com.siwol025.flight_monitor.domain.flight.FlightSeatPrice;
import com.siwol025.flight_monitor.domain.flight.SeatGrade;
import java.math.BigDecimal;
import lombok.Builder;

@Builder
public record MockFlightSeatPriceResponse(
        String flightNumber,
        SeatGrade seatGrade,
        BigDecimal price
) {
    public static MockFlightSeatPriceResponse of(FlightSeatPrice flightSeatPrice) {
        return MockFlightSeatPriceResponse.builder()
                .flightNumber(flightSeatPrice.getFlight().getFlightNumber())
                .seatGrade(flightSeatPrice.getSeatGrade())
                .price(flightSeatPrice.getPrice())
                .build();
    }
}
