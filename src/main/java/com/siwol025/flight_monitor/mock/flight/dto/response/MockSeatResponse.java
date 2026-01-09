package com.siwol025.flight_monitor.mock.flight.dto.response;

import com.siwol025.flight_monitor.domain.flight.Seat;
import com.siwol025.flight_monitor.domain.flight.SeatGrade;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "가상의 외부 항공편시스템의 좌석 API 응답 DTO")
public record MockSeatResponse(
        @Schema(description = "좌석 아이디", example = "101")
        Long id,

        @Schema(description = "항공편 명", example = "KE101")
        String flightNumber,

        @Schema(description = "좌석 등급", example = "ECONOMY")
        SeatGrade seatGrade,

        @Schema(description = "좌석 번호", example = "45B")
        String seatNumber,

        @Schema(description = "예약 가능 여부(true = 예약 불가, false = 예약 가능", example = "false")
        boolean isBooked
) {
    public static MockSeatResponse of(Seat seat) {
        return MockSeatResponse.builder()
                .id(seat.getId())
                .flightNumber(seat.getFlight().getFlightNumber())
                .seatGrade(seat.getSeatGrade())
                .seatNumber(seat.getSeatNumber())
                .isBooked(seat.isBooked())
                .build();
    }
}
