package com.siwol025.flight_monitor.booking.controller;

import com.siwol025.flight_monitor.booking.dto.request.BookingRequest;
import com.siwol025.flight_monitor.booking.dto.response.BookingDetailResponse;
import com.siwol025.flight_monitor.booking.dto.response.BookingResponse;
import com.siwol025.flight_monitor.user.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;

@Tag(name = "Booking", description = "항공권 구매 관련 API")
public interface BookingControllerSwagger {

    @Operation(
            summary = "항공권 구매(좌석을 잡아 두는 hold 주문)",
            description = "Idempotency-Key 필수. 결제 직전 재조회 → 예매 가능·만료·성인·금액 확인 → 업체 hold 주문 → "
                    + "결제 대기(HELD, Toss 결제창 값 포함). 같은 키 재요청은 저장된 응답을 그대로 돌려준다",
            responses = {
                    @ApiResponse(responseCode = "201", description = "hold 성공(HELD, payment에 결제창 값)"),
                    @ApiResponse(responseCode = "202", description = "hold 결과 확인 중(HOLD_REQUESTED). 예매 조회로 진행을 본다"),
                    @ApiResponse(responseCode = "400", description = "입력 오류, 키 없음(IDEMPOTENCY_KEY_REQUIRED), "
                            + "미성년 승객(PASSENGER_NOT_ADULT), 업체 주문 거절(ORDER_REJECTED)"),
                    @ApiResponse(responseCode = "409", description = "같은 키 처리 중(IDEMPOTENT_REQUEST_PROCESSING), "
                            + "예매 불가(OFFER_UNAVAILABLE·OFFER_NOT_HOLDABLE·OFFER_ALREADY_BOOKED), 만료(OFFER_EXPIRED), "
                            + "가격 변동(OFFER_PRICE_CHANGED, 현재 금액·통화 포함)"),
                    @ApiResponse(responseCode = "422", description = "같은 키로 다른 요청(IDEMPOTENCY_KEY_REUSED)"),
                    @ApiResponse(responseCode = "503", description = "항공권 업체 장애·호출 한도 초과(같은 키로 다시 시도)")
            }
    )
    ResponseEntity<?> book(User user, String idempotencyKey, BookingRequest request);

    @Operation(
            summary = "예매 진행 상태 조회",
            description = "결제 뒤 이 API를 폴링해 발권(TICKETED)까지 본다. HELD면 결제창 값(payment)을 담는다",
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공(status, 원화 금액, 예약 번호)"),
                    @ApiResponse(responseCode = "404", description = "없는 예매·남의 예매(BOOKING_NOT_FOUND)")
            }
    )
    ResponseEntity<BookingDetailResponse> getBooking(User user, long bookingId);

    @Operation(
            summary = "내 구매 내역 (최근순, 진행 상태 포함)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공")
            }
    )
    ResponseEntity<List<BookingResponse>> getMyBookings(User user);
}
