package com.siwol025.flight_monitor.mock.airport.dto.response;

import com.siwol025.flight_monitor.domain.airport.Airport;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "외부 공항 정보 응답 DTO")
public record MockAirportResponse (
        @Schema(description = "공항 코드", example = "ICN")
        String code,
        @Schema(description = "공항 이름", example = "인천국제공항")
        String name
) {
    public static MockAirportResponse of(Airport airport) {
        return MockAirportResponse.builder()
                .code(airport.getAirportCode())
                .name(airport.getAirportName())
                .build();
    }
}
