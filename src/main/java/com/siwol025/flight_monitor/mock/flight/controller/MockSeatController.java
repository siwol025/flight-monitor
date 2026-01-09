package com.siwol025.flight_monitor.mock.flight.controller;

import com.siwol025.flight_monitor.domain.flight.SeatGrade;
import com.siwol025.flight_monitor.mock.flight.dto.request.MockSeatBulkRequest;
import com.siwol025.flight_monitor.mock.flight.dto.response.MockSeatResponse;
import com.siwol025.flight_monitor.mock.flight.service.MockSeatService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping
    public ResponseEntity<List<MockSeatResponse>> getSeats() {
        return ResponseEntity.ok(mockSeatService.readSeats());
    }

    @GetMapping("/{flightId}")
    public ResponseEntity<List<MockSeatResponse>> getSeatsByFlight(@PathVariable Long flightId) {
        return ResponseEntity.ok(mockSeatService.readSeatsByFlight(flightId));
    }

    @GetMapping("/{flightId}/filter")
    public ResponseEntity<List<MockSeatResponse>> getSeatsByFlightAndSeatGrade(@PathVariable Long flightId, @RequestParam SeatGrade seatGrade) {
        return ResponseEntity.ok(mockSeatService.readSeatsByFlightAndSeatGrade(flightId, seatGrade));
    }

    @PatchMapping("/{seatId}/reserve")
    public ResponseEntity<Void> reserveSeat(@PathVariable Long seatId) {
        mockSeatService.reserveSeat(seatId);
        return ResponseEntity.noContent().build();
    }
}
