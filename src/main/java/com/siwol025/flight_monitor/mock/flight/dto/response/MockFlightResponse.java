package com.siwol025.flight_monitor.mock.flight.dto.response;

import com.siwol025.flight_monitor.domain.flight.Flight;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;

@Builder
@Schema(description = "가상의 외부 항공편시스템의 항공편 API 응답 DTO")
public record MockFlightResponse(
        @Schema(description = "항공편 아이디", example = "123")
        Long id,

        @Schema(description = "항공편 명", example = "KE101")
        String flightNumber,

        @Schema(description = "항공사 코드", example = "KE")
        String airlineCode,

        @Schema(description = "출발 공항 코드", example = "ICN")
        String departureAirportCode,

        @Schema(description = "도착 공항 코드", example = "NRT")
        String arrivalAirportCode,

        @Schema(description = "출발 시간", example = "2026-05-10T10:30:00", type = "string")
        LocalDateTime departureTime,

        @Schema(description = "도착 예상 시간", example = "2026-05-10T12:30:00", type = "string")
        LocalDateTime arrivalTime,

        @Schema(description = "좌석 등급별 가격")
        List<MockFlightSeatPriceResponse> seatPrices
) {
    public static MockFlightResponse of(Flight flight) {
        return MockFlightResponse.builder()
                .id(flight.getId())
                .flightNumber(flight.getFlightNumber())
                .airlineCode(flight.getAirline().getAirlineCode())
                .departureAirportCode(flight.getDepartureAirport().getAirportCode())
                .arrivalAirportCode(flight.getArrivalAirport().getAirportCode())
                .departureTime(flight.getDepartureTime())
                .arrivalTime(flight.getArrivalTime())
                .seatPrices(flight.getFlightSeatPrices().stream()
                        .map(MockFlightSeatPriceResponse::of)
                        .toList())
                .build();
    }
}
