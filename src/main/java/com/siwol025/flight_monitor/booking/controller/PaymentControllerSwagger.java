package com.siwol025.flight_monitor.booking.controller;

import com.siwol025.flight_monitor.booking.dto.request.PaymentConfirmRequest;
import com.siwol025.flight_monitor.user.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Payment", description = "결제 승인 API")
public interface PaymentControllerSwagger {

    @Operation(
            summary = "토스페이먼츠 결제 승인",
            description = "Idempotency-Key 필수. Toss 결제창 successUrl의 orderId·paymentKey·amount로 승인한다. "
                    + "같은 키 재요청은 저장된 응답을 그대로 돌려준다. 승인 뒤 발권 진행은 예매 조회로 본다",
            responses = {
                    @ApiResponse(responseCode = "202", description = "승인 완료(PAYMENT_APPROVED) 또는 결과 확인 중"
                            + "(PAYMENT_CONFIRMING, 다시 결제하지 말고 예매 조회로 진행을 본다)"),
                    @ApiResponse(responseCode = "400", description = "입력 오류, 키 없음(IDEMPOTENCY_KEY_REQUIRED), "
                            + "금액 불일치(PAYMENT_AMOUNT_MISMATCH), 결제 거절(PAYMENT_REJECTED, 새 결제창으로 다시 결제)"),
                    @ApiResponse(responseCode = "404", description = "없는 주문번호·남의 예매(BOOKING_NOT_FOUND)"),
                    @ApiResponse(responseCode = "409", description = "같은 키 처리 중(IDEMPOTENT_REQUEST_PROCESSING), "
                            + "승인 기한 지남(PAYMENT_EXPIRED, 새 결제창으로 다시 결제), 결제할 수 없는 상태(BOOKING_NOT_PAYABLE)"),
                    @ApiResponse(responseCode = "422", description = "같은 키로 다른 요청(IDEMPOTENCY_KEY_REUSED)"),
                    @ApiResponse(responseCode = "503", description = "결제사 연동 장애(승인되지 않음 확인, 같은 키로 다시 시도)")
            }
    )
    ResponseEntity<?> confirm(User user, String idempotencyKey, PaymentConfirmRequest request);
}
