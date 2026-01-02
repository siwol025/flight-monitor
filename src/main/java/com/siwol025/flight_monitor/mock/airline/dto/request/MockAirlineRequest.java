package com.siwol025.flight_monitor.mock.airline.dto.request;

import com.siwol025.flight_monitor.domain.airline.Airline;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

@Schema(description = "가상의 외부 항공편시스템 항공사 데이터 API 요청 DTO", name = "MockAirlineRequest")
public record MockAirlineRequest(
        @NotEmpty(message = "항공사 이름은 빈 값일 수 없습니다.")
        @Schema(description = "항공사 이름", example = "대한항공")
        String name
) {
    public Airline toAirline() {
        return Airline.builder()
                .airlineName(name)
                .build();
    }
}
