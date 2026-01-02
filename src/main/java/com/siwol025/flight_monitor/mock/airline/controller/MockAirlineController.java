package com.siwol025.flight_monitor.mock.airline.controller;

import com.siwol025.flight_monitor.mock.airline.dto.request.MockAirlineRequest;
import com.siwol025.flight_monitor.mock.airline.dto.response.MockAirlineResponse;
import com.siwol025.flight_monitor.mock.airline.service.MockAirlineService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/external/api/airlines")
@RequiredArgsConstructor
public class MockAirlineController implements MockAirlineControllerSwagger{
    private final MockAirlineService mockAirlineService;

    @GetMapping
    public ResponseEntity<List<MockAirlineResponse>> getAirlines() {
        return ResponseEntity.ok(mockAirlineService.getAirlines());
    }

    @PostMapping
    public ResponseEntity<Long> addAirline(MockAirlineRequest request) {
        Long airlineId = mockAirlineService.createAirline(request);
        return ResponseEntity.ok(airlineId);
    }
}
