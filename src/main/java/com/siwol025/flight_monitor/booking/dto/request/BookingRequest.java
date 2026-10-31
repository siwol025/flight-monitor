package com.siwol025.flight_monitor.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * 구매 요청. Duffel 주문 생성 형식을 따른다(offerId = selected_offers[0], amount·currency = payments[0]).
 * 금액·통화는 사용자가 결제 직전 재조회에서 본 값이다.
 */
@Schema(description = "구매 요청", name = "BookingRequest")
public record BookingRequest(
        @Schema(description = "결제 직전 재조회한 항공권 ID", example = "off_mock_1")
        @NotBlank(message = "offerId는 필수입니다.")
        String offerId,

        @Schema(description = "재조회에서 본 금액", example = "250000.00")
        @NotNull(message = "amount는 필수입니다.")
        @Positive(message = "amount는 0보다 커야 합니다.")
        BigDecimal amount,

        @Schema(description = "재조회에서 본 통화 (ISO 4217)", example = "USD")
        @NotNull(message = "currency는 필수입니다.")
        @Pattern(regexp = "[A-Z]{3}", message = "currency는 영문 대문자 3자여야 합니다.")
        String currency,

        @NotNull(message = "passenger는 필수입니다.")
        @Valid
        PassengerRequest passenger
) {
}
