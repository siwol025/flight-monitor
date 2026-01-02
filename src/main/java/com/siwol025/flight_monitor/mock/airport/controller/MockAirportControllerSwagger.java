package com.siwol025.flight_monitor.mock.airport.controller;

import com.siwol025.flight_monitor.mock.airport.dto.request.MockAirportRequest;
import com.siwol025.flight_monitor.mock.airport.dto.response.MockAirportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;

@Tag(name = "External Mock Airport API", description = "가상의 외부 공항 데이터 API")
public interface MockAirportControllerSwagger {

    @Operation(
            summary = "전체 공항 목록 조회",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "조회 성공"
                    )
            }
    )
    ResponseEntity<List<MockAirportResponse>> getAirports();

    @Operation(
            summary = "신규 공항 등록",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "등록 성공"
                    )
            }
    )
    ResponseEntity<Long> addAirport(MockAirportRequest request);

    @Operation(
            summary = "공항 이름 변경",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "변경 성공"
                    )
            }
    )
    ResponseEntity<MockAirportResponse> editAirport(MockAirportRequest request);
}
