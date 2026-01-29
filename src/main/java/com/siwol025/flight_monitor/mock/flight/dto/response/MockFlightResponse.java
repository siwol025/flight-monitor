package com.siwol025.flight_monitor.mock.flight.dto.response;

import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import com.siwol025.flight_monitor.mock.flight.domain.MockFlight;
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

        @Schema(description = "예약 가능 좌석 존재 여부")
        boolean isSeatAvailable,

        @Schema(description = "좌석 등급별 가격")
        List<MockFlightSeatPriceResponse> seatPrices
) {
    public static MockFlightResponse from(MockFlight mockFlight) {
        return MockFlightResponse.builder()
                .id(mockFlight.getId())
                .flightNumber(mockFlight.getFlightNumber())
                .airlineCode(mockFlight.getAirline().getAirlineCode())
                .departureAirportCode(mockFlight.getDepartureAirport().getAirportCode())
                .arrivalAirportCode(mockFlight.getArrivalAirport().getAirportCode())
                .departureTime(mockFlight.getDepartureTime())
                .arrivalTime(mockFlight.getArrivalTime())
                .isSeatAvailable(mockFlight.getSeats().stream()
                        .anyMatch(s -> !s.isBooked()))
                .seatPrices(mockFlight.getFlightSeatPrices().stream()
                        .map(MockFlightSeatPriceResponse::of)
                        .toList())
                .build();
    }

    public static MockFlightResponse of(MockFlight mockFlight, SeatGrade targetGrade) {
        return MockFlightResponse.builder()
                .id(mockFlight.getId())
                .flightNumber(mockFlight.getFlightNumber())
                .airlineCode(mockFlight.getAirline().getAirlineCode())
                .departureAirportCode(mockFlight.getDepartureAirport().getAirportCode())
                .arrivalAirportCode(mockFlight.getArrivalAirport().getAirportCode())
                .departureTime(mockFlight.getDepartureTime())
                .arrivalTime(mockFlight.getArrivalTime())
                .isSeatAvailable(mockFlight.getSeats().stream()
                        .anyMatch(seat -> seat.getSeatGrade() == targetGrade && !seat.isBooked()))
                .seatPrices(mockFlight.getFlightSeatPrices().stream()
                        .map(MockFlightSeatPriceResponse::of)
                        .toList())
                .build();
    }
}
