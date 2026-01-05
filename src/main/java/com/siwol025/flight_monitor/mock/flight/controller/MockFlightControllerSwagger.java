package com.siwol025.flight_monitor.mock.flight.controller;

import com.siwol025.flight_monitor.mock.flight.dto.request.MockFlightRequest;
import com.siwol025.flight_monitor.mock.flight.dto.response.MockFlightResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "External Mock Flight API", description = "가상의 외부 항공편시스템의 항공편 데이터 API")
public interface MockFlightControllerSwagger {

    @Operation(
            summary = "신규 항공편 등록",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "등록 성공"
                    )
            }
    )
    ResponseEntity<Long> addFlight(MockFlightRequest request);

    @Operation(
            summary = "항공 조건 검색",
            description = "출발/도착 공항 코드와 출발 날짜로 항공편을 조회합니다.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "조회 성공"
                    )
            }
    )
    ResponseEntity<List<MockFlightResponse>> searchFlights(@Parameter(description = "출발 공항 코드 (예: ICN)", example = "ICN")
                                                           String departureAirportCode,

                                                           @Parameter(description = "도착 공항 코드 (예: NRT)", example = "NRT")
                                                           String arrivalAirportCode,

                                                           @Parameter(description = "출발 날짜 (yyyy-MM-dd)", example = "2026-05-10")
                                                           LocalDate departureDate);

    @Operation(
            summary = "항공편 리스트 검색",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "조회 성공"
                    )
            }
    )
    ResponseEntity<List<MockFlightResponse>> getFlights();

    @Operation(
            summary = "항공편 상세 조회",
            description = "특정 항공편 하나를 ID로 상세 조회하는 API",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "조회 성공"
                    )
            }
    )
    ResponseEntity<MockFlightResponse> getFlight(Long flightId);
}
