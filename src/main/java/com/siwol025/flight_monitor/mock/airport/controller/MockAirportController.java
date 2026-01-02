package com.siwol025.flight_monitor.mock.airport.controller;

import com.siwol025.flight_monitor.mock.airport.dto.request.MockAirportRequest;
import com.siwol025.flight_monitor.mock.airport.dto.response.MockAirportResponse;
import com.siwol025.flight_monitor.mock.airport.service.MockAirportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/external/airports")
public class MockAirportController implements MockAirportControllerSwagger{
    private final MockAirportService mockAirportService;

    @GetMapping
    public ResponseEntity<List<MockAirportResponse>> getAirports() {
        return ResponseEntity.ok(mockAirportService.getAirports());
    }

    @PostMapping
    public ResponseEntity<Long> addAirport(@Valid @RequestBody MockAirportRequest request) {
        Long airportId = mockAirportService.createAirport(request);
        return ResponseEntity.ok(airportId);
    }
}
