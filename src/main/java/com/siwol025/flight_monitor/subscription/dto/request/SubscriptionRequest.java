package com.siwol025.flight_monitor.subscription.dto.request;

import com.siwol025.flight_monitor.route.domain.SeatGrade;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 공항 코드 형식·과거 출발일·출발지=도착지 검증은 노선 키(RouteKey)가 한 곳에서 한다(소문자 입력은 대문자로 정규화).
 */
@Schema(description = "구독 요청 dto", name = "SubscriptionRequest")
public record SubscriptionRequest(
        @Schema(description = "출발 공항 IATA 코드", example = "ICN")
        @NotNull(message = "departureAirport는 필수입니다.")
        String departureAirport,

        @Schema(description = "도착 공항 IATA 코드", example = "NRT")
        @NotNull(message = "arrivalAirport는 필수입니다.")
        String arrivalAirport,

        @Schema(description = "출발일 (한국 날짜 기준 오늘 이후)", example = "2026-11-26")
        @NotNull(message = "departureDate는 필수입니다.")
        LocalDate departureDate,

        @Schema(description = "좌석 등급 (ECONOMY, BUSINESS, FIRST)", example = "ECONOMY")
        @NotNull(message = "seatGrade는 필수입니다.")
        SeatGrade seatGrade,

        @Schema(description = "목표 가격 (null이면 조건 없음, 노선 최저가의 통화 기준)", example = "120.00")
        @DecimalMin(value = "0.01", message = "목표 가격은 0보다 커야 합니다.")
        BigDecimal targetPrice,

        @Schema(description = "하락률 임계값 % (null이면 조건 없음, 0.01 ~ 100.00)", example = "10.00")
        @DecimalMin(value = "0.01", message = "하락률 임계값은 0보다 커야 합니다.")
        @DecimalMax(value = "100.00", message = "하락률 임계값은 100 이하여야 합니다.")
        BigDecimal dropThresholdPercent
) {
}
