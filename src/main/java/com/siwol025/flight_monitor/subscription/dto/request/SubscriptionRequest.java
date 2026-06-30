package com.siwol025.flight_monitor.subscription.dto.request;

import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "구독 요청 dto", name = "SubscriptionRequest")
public record SubscriptionRequest(
        @Schema(description = "항공편 ID", example = "1")
        @NotNull(message = "flightId는 필수입니다.")
        Long flightId,

        @Schema(description = "좌석 등급 (ECONOMY, BUSINESS, FIRST)", example = "ECONOMY")
        @NotNull(message = "seatGrade는 필수입니다.")
        SeatGrade seatGrade
) {
}
