package com.siwol025.flight_monitor.mock.flight.controller;

import com.siwol025.flight_monitor.mock.flight.dto.request.MockSeatBulkRequest;
import com.siwol025.flight_monitor.mock.flight.service.MockSeatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/external/api/seats")
public class MockSeatController implements MockSeatControllerSwagger{
    private final MockSeatService mockSeatService;

    @PostMapping("/{flightId}")
    public ResponseEntity<Void> createBulkSeats(@PathVariable Long flightId, @RequestBody MockSeatBulkRequest request) {
        mockSeatService.createBulkSeats(flightId, request);
        return ResponseEntity.noContent().build(); 
    }
}
