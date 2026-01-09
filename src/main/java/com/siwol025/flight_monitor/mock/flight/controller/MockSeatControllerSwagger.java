package com.siwol025.flight_monitor.mock.flight.controller;

import com.siwol025.flight_monitor.mock.flight.dto.request.MockSeatBulkRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Mock Seat API", description = "가상 좌석 생성 및 관리 API")
public interface MockSeatControllerSwagger {

    @Operation(
            summary = "등급별 좌석 일괄 등록",
            description = "특정 항공편에 규칙에 따른 좌석을 대량으로 생성합니다.",
            responses = {
                    @ApiResponse(
                            responseCode = "204",
                            description = "등록 성공"
                    )
            }
    )
    public ResponseEntity<Void> createBulkSeats(Long flightId, MockSeatBulkRequest request);
}
