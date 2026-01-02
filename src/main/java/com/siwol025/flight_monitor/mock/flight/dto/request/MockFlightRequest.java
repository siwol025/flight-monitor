package com.siwol025.flight_monitor.mock.flight.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.siwol025.flight_monitor.domain.airline.Airline;
import com.siwol025.flight_monitor.domain.airport.Airport;
import com.siwol025.flight_monitor.domain.flight.Flight;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Schema(description = "가상의 외부 항공편시스템의 항공편 데이터 API 요청 DTO", name = "MockFlightRequest")
public record MockFlightRequest(
        @NotEmpty(message = "항공편 명은 빈 값일 수 없습니다.")
        @Schema(description = "항공편 명", example = "KE101")
        String flightNumber,

        @NotEmpty(message = "항공사 코드는 빈 값일 수 없습니다.")
        @Schema(description = "항공사 코드", example = "KE")
        String airlineCode,

        @NotEmpty(message = "공항 코드는 빈 값일 수 없습니다.")
        @Schema(description = "출발 공항 코드", example = "ICN")
        String departureAirportCode,

        @NotEmpty(message = "공항 코드는 빈 값일 수 없습니다.")
        @Schema(description = "도착 공항 코드", example = "NRT")
        String arrivalAirportCode,

        @NotNull(message = "출발시간은 빈 값일 수 없습니다.")
        @Schema(description = "출발 시간", example = "2026-05-10T10:30:00", type = "string")
        LocalDateTime departureTime,

        @NotNull(message = "도착 예상 시간은 빈 값일 수 없습니다.")
        @Schema(description = "도착 예상 시간", example = "2026-05-10T12:30:00", type = "string")
        LocalDateTime arrivalTime
) {
    public Flight toFlight(Airline airline,
                           Airport departureAirport,
                           Airport arrivalAirport) {
        return Flight.builder()
                .flightNumber(flightNumber)
                .airline(airline)
                .departureAirport(departureAirport)
                .arrivalAirport(arrivalAirport)
                .departureTime(departureTime)
                .arrivalTime(arrivalTime)
                .build();
    }
}
