package com.siwol025.flight_monitor.mock.airline.controller;

import com.siwol025.flight_monitor.mock.airline.dto.request.MockAirlineRequest;
import com.siwol025.flight_monitor.mock.airline.dto.response.MockAirlineResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;

@Tag(name = "External Mock Airline API", description = "가상의 외부 항공편시스템 항공사 데이터 API")
public interface MockAirlineControllerSwagger {

    @Operation(
            summary = "전체 항공사 목록 조회",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "조회 성공"
                    )
            }
    )
    ResponseEntity<List<MockAirlineResponse>> getAirlines();

    @Operation(
            summary = "신규 항공사 등록",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "등록 성공"
                    )
            }
    )
    ResponseEntity<Long> addAirline(MockAirlineRequest request);

    @Operation(
            summary = "항공사 정보 삭제",
            responses = {
                    @ApiResponse(
                            responseCode = "204",
                            description = "삭제 성공"
                    )
            }
    )
    ResponseEntity<Void> deleteAirline(String airlineCode);
}
