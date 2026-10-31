package com.siwol025.flight_monitor.booking.dto.request;

import com.siwol025.flight_monitor.route.domain.SeatGrade;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 항공권 검색 조건(쿼리 파라미터). 공항 코드 형식·과거 출발일·출발지=도착지 검증은 노선 키(RouteKey)가 한 곳에서 한다.
 */
@Schema(description = "항공권 검색 조건", name = "FlightOfferSearchRequest")
public record FlightOfferSearchRequest(
        @Schema(description = "출발 공항 IATA 코드", example = "ICN")
        @NotNull(message = "departureAirport는 필수입니다.")
        String departureAirport,

        @Schema(description = "도착 공항 IATA 코드", example = "NRT")
        @NotNull(message = "arrivalAirport는 필수입니다.")
        String arrivalAirport,

        @Schema(description = "출발일 (한국 날짜 기준 오늘 이후)", example = "2026-11-26")
        @NotNull(message = "departureDate는 필수입니다.")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate departureDate,

        @Schema(description = "좌석 등급 (ECONOMY, BUSINESS, FIRST)", example = "ECONOMY")
        @NotNull(message = "seatGrade는 필수입니다.")
        SeatGrade seatGrade
) {
}
