package com.siwol025.flight_monitor.mock.flight.dto.request;

import com.siwol025.flight_monitor.domain.flight.SeatGrade;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

@Schema(description = "가상 좌석 가격 등록/수정 요청", name = "MockFlightSeatPriceRequest")
public record MockFlightSeatPriceRequest(
        @Schema(description = "항공편 ID", example = "1")
        @NotNull
        Long flightId,

        @Schema(description = "좌석 등급 (ECONOMY, BUSINESS, FIRST)", example = "ECONOMY")
        @NotNull
        SeatGrade seatGrade,

        @Schema(description = "가격", example = "155000")
        @NotNull
        @Positive
        BigDecimal price
) {
}
