package com.siwol025.flight_monitor.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 결제 승인 요청. Toss 결제창 successUrl 쿼리 값(orderId, paymentKey, amount) 그대로다.
 * amount는 예매에 고정한 고객 결제 금액과 같아야 한다(승인에는 저장한 금액을 보낸다).
 */
@Schema(description = "결제 승인 요청", name = "PaymentConfirmRequest")
public record PaymentConfirmRequest(
        @Schema(description = "Toss 주문번호(구매 응답의 payment.orderId)", example = "fm-6f1c2c1e-0d1b-4c1e-9a55-2b1f0f6a7c01")
        @NotBlank(message = "orderId는 필수입니다.")
        String orderId,

        @Schema(description = "Toss 결제 키(successUrl의 paymentKey)", example = "tviva20261105abcdef")
        @NotBlank(message = "paymentKey는 필수입니다.")
        String paymentKey,

        @Schema(description = "결제 금액(원, successUrl의 amount)", example = "106456")
        @NotNull(message = "amount는 필수입니다.")
        @Positive(message = "amount는 0보다 커야 합니다.")
        Long amount
) {
}
