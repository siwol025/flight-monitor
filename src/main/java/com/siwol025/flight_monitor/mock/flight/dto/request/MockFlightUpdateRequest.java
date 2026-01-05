package com.siwol025.flight_monitor.mock.flight.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Schema(description = "가상 항공편 수정 요청 DTO")
public record MockFlightUpdateRequest(
        @NotEmpty(message = "항공편 명은 빈 값일 수 없습니다.")
        @Schema(description = "항공편 명", example = "KE102")
        String flightNumber,

        @NotNull(message = "출발시간은 빈 값일 수 없습니다.")
        @Schema(description = "출발 시간", example = "2026-05-10T15:30:00", type = "string")
        LocalDateTime departureTime,

        @NotNull(message = "도착 예상 시간은 빈 값일 수 없습니다.")
        @Schema(description = "도착 예상 시간", example = "2026-05-10T17:30:00", type = "string")
        LocalDateTime arrivalTime
) {}
