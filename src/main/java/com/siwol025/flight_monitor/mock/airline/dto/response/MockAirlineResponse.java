package com.siwol025.flight_monitor.mock.airline.dto.response;

import com.siwol025.flight_monitor.domain.airline.Airline;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "가상의 외부 항공편 API 항공사 데이터 응답 DTO")
public record MockAirlineResponse(
        @Schema(description = "항공사 코드", example = "KE")
        String code,

        @Schema(description = "항공사 이름", example = "대한항공")
        String name
) {
    public static MockAirlineResponse of(Airline airline) {
        return MockAirlineResponse.builder()
                .name(airline.getAirlineName())
                .build();
    }
}
