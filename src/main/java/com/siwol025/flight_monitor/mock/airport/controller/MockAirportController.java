package com.siwol025.flight_monitor.mock.airport.controller;

import com.siwol025.flight_monitor.mock.airport.dto.response.MockAirportResponse;
import com.siwol025.flight_monitor.mock.airport.service.MockAirportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "External Mock Airport API", description = "가상의 외부 공항 데이터 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/external")
public class MockAirportController {
    private final MockAirportService mockAirportService;

    @Operation(summary = "전체 공항 목록 조회", description = "외부 시스템에 등록된 모든 공항 정보를 가져옵니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/airports")
    public ResponseEntity<List<MockAirportResponse>> getAirports() {
        return ResponseEntity.ok(mockAirportService.getAirports());
    }
}
