package com.siwol025.flight_monitor.booking.controller;

import com.siwol025.flight_monitor.booking.dto.request.BookingRequest;
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
            summary = "항공권 구매",
            description = "결제 직전 재조회 → 예매 가능·금액·통화 확인 → 업체 주문 → 구매 내역 저장",
            responses = {
                    @ApiResponse(responseCode = "201", description = "구매 성공"),
                    @ApiResponse(responseCode = "400", description = "입력 오류, 미성년 승객"),
                    @ApiResponse(responseCode = "409", description = "예매 불가(OFFER_UNAVAILABLE), "
                            + "가격 변동(OFFER_PRICE_CHANGED, 재조회 단계면 현재 금액·통화 포함)"),
                    @ApiResponse(responseCode = "503", description = "항공권 업체 장애·호출 한도 초과")
            }
    )
    ResponseEntity<BookingResponse> book(User user, BookingRequest request);

    @Operation(
            summary = "내 구매 내역 (최근순)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공")
            }
    )
    ResponseEntity<List<BookingResponse>> getMyBookings(User user);
}
