package com.siwol025.flight_monitor.booking.controller;

import com.siwol025.flight_monitor.booking.dto.request.FlightOfferSearchRequest;
import com.siwol025.flight_monitor.booking.dto.response.FlightOfferResponse;
import com.siwol025.flight_monitor.user.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;

@Tag(name = "FlightOffer", description = "예매용 항공권 검색·재조회 API")
public interface FlightOfferControllerSwagger {

    @Operation(
            summary = "예매 가능한 항공권 목록 (가격 오름차순)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공(없으면 빈 목록)"),
                    @ApiResponse(responseCode = "400", description = "입력 오류, 업체가 조회할 수 없는 노선"),
                    @ApiResponse(responseCode = "503", description = "항공권 업체 장애·호출 한도 초과")
            }
    )
    ResponseEntity<List<FlightOfferResponse>> searchOffers(User user, FlightOfferSearchRequest request);

    @Operation(
            summary = "결제 직전 항공권 재조회",
            responses = {
                    @ApiResponse(responseCode = "200", description = "예매 가능, 현재 금액·통화"),
                    @ApiResponse(responseCode = "409", description = "예매 불가(OFFER_UNAVAILABLE)"),
                    @ApiResponse(responseCode = "503", description = "항공권 업체 장애·호출 한도 초과")
            }
    )
    ResponseEntity<FlightOfferResponse> getOffer(User user, String offerId);
}
