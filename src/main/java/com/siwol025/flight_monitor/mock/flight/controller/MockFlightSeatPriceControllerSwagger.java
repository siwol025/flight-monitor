package com.siwol025.flight_monitor.mock.flight.controller;

import com.siwol025.flight_monitor.mock.flight.dto.request.MockFlightSeatPriceRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "External Mock Price API", description = "가상의 외부 항공편시스템의 좌석 가격 관리 API")
public interface MockFlightSeatPriceControllerSwagger {
    @Operation(
            summary = "좌석 가격 등록 및 수정",
            description = "특정 항공편의 좌석 등급별 가격을 등록합니다. 이미 존재하면 가격이 업데이트됩니다.",
            responses = {
                    @ApiResponse(
                            responseCode = "204",
                            description = "등록 성공"
                    )
            }
    )
    ResponseEntity<Void> upsertPrice(MockFlightSeatPriceRequest request);
}
