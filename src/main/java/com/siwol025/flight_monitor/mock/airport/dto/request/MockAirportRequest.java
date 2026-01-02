package com.siwol025.flight_monitor.mock.airport.dto.request;

import com.siwol025.flight_monitor.domain.airport.Airport;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

@Schema(description = "가상의 외부 항공편시스템의 공항 데이터 API 요청 DTO", name = "MockAirportRequest")
public record MockAirportRequest(
        @NotEmpty(message = "공항 코드는 빈 값일 수 없습니다.")
        @Schema(description = "공항 코드", example = "ICN")
        String code,

        @NotEmpty(message = "공항 이름은 빈 값일 수 없습니다.")
        @Schema(description = "공항 이름", example = "인천국제공항")
        String name
) {
    public Airport toAirport() {
        return Airport.builder()
                .airportCode(code)
                .airportName(name)
                .build();
    }
}
