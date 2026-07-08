package com.siwol025.flight_monitor.subscription.dto.request;

import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Schema(description = "구독 요청 dto", name = "SubscriptionRequest")
public record SubscriptionRequest(
        @Schema(description = "항공편 ID", example = "1")
        @NotNull(message = "flightId는 필수입니다.")
        Long flightId,

        @Schema(description = "좌석 등급 (ECONOMY, BUSINESS, FIRST)", example = "ECONOMY")
        @NotNull(message = "seatGrade는 필수입니다.")
        SeatGrade seatGrade,

        @Schema(description = "목표 가격 (null이면 조건 없음)", example = "120000.00")
        @DecimalMin(value = "0.01", message = "목표 가격은 0보다 커야 합니다.")
        BigDecimal targetPrice,

        @Schema(description = "하락률 임계값 % (null이면 조건 없음, 0.01 ~ 100.00)", example = "10.00")
        @DecimalMin(value = "0.01", message = "하락률 임계값은 0보다 커야 합니다.")
        @DecimalMax(value = "100.00", message = "하락률 임계값은 100 이하여야 합니다.")
        BigDecimal dropThresholdPercent
) {
}
