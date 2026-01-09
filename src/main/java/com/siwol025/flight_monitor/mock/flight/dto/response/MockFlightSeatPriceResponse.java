package com.siwol025.flight_monitor.mock.flight.dto.response;

import com.siwol025.flight_monitor.domain.flight.FlightSeatPrice;
import com.siwol025.flight_monitor.domain.flight.SeatGrade;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.Builder;

@Builder
@Schema(description = "가상의 외부 항공편시스템의 좌석별 가격 API 응답 DTO")
public record MockFlightSeatPriceResponse(
        @Schema(description = "항공편 명", example = "KE101")
        String flightNumber,

        @Schema(description = "좌석 등급", example = "ECONOMY")
        SeatGrade seatGrade,

        @Schema(description = "좌석 가격", example = "155000")
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
