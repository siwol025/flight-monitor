package com.siwol025.flight_monitor.mock.flight.controller;

import com.siwol025.flight_monitor.mock.flight.dto.request.MockFlightRequest;
import com.siwol025.flight_monitor.mock.flight.service.MockFlightService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/external/api/flights")
public class MockFlightController implements MockFlightControllerSwagger{
    private final MockFlightService mockFlightService;

    @PostMapping
    public ResponseEntity<Long> addFlight(MockFlightRequest request) {
        Long flightId = mockFlightService.createFlight(request);
        return ResponseEntity.ok(flightId);
    }
}
