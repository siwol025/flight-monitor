package com.siwol025.flight_monitor.mock.flight.dto.request;

import com.siwol025.flight_monitor.domain.flight.SeatGrade;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

@Schema(description = "가상 항공편 좌석 일괄 등록 요청 DTO")
public record MockSeatBulkRequest(
        @NotEmpty(message = "좌석 등급값은 빈 값일 수 없습니다.")
        @Schema(description = "좌석 등급 (ECONOMY, BUSINESS, FIRST)", example = "ECONOMY")
        SeatGrade seatGrade,

        @NotNull(message = "시작 행은 빈 값일 수 없습니다.")
        @Schema(description = "시작 행", example = "1")
        int startRow,

        @NotNull(message = "종료 행은 빈 값일 수 없습니다.")
        @Schema(description = "종료 행", example = "6")
        int endRow,

        @NotEmpty(message = "좌석 구성은 빈 값일 수 없습니다.")
        @Schema(description = "한 행의 좌석 구성", example = "ABCDEF")
        String seatSymbols
) {
}
