package com.siwol025.flight_monitor.mock.flight.controller;

import com.siwol025.flight_monitor.mock.flight.dto.request.MockFlightRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

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
}
