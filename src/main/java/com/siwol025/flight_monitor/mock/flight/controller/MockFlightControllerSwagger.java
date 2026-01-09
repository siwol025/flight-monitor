package com.siwol025.flight_monitor.mock.flight.controller;

import com.siwol025.flight_monitor.domain.flight.SeatGrade;
import com.siwol025.flight_monitor.mock.flight.dto.request.MockFlightRequest;
import com.siwol025.flight_monitor.mock.flight.dto.request.MockFlightUpdateRequest;
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
            summary = "항공편 조건 검색",
            description = "출발/도착 공항 코드와 출발 날짜, 좌석 등급으로 항공편을 조회합니다.",
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
                                                           LocalDate departureDate,

                                                           @Parameter(description = "좌석 등급 (예: ECONOMY, BUSINESS, FIRST)", example = "ECONOMY")
                                                           SeatGrade seatGrade);

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

    @Operation(
            summary = "항공편 수정",
            description = "항공편의 편명, 출발 시간, 도착 시간을 수정하는 API",
            responses = {
                    @ApiResponse(
                            responseCode = "204",
                            description = "수정 성공"
                    )
            }
    )
    ResponseEntity<Void> editFlight(Long flightId, MockFlightUpdateRequest request);

    @Operation(
            summary = "항공편 삭제",
            responses = {
                    @ApiResponse(
                            responseCode = "204",
                            description = "삭제 성공"
                    )
            }
    )
    ResponseEntity<Void> deleteFlight(Long flightId);
}
