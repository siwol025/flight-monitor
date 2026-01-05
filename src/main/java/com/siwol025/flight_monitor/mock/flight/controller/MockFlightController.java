package com.siwol025.flight_monitor.mock.flight.controller;

import com.siwol025.flight_monitor.mock.flight.dto.request.MockFlightRequest;
import com.siwol025.flight_monitor.mock.flight.dto.response.MockFlightResponse;
import com.siwol025.flight_monitor.mock.flight.service.MockFlightService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping("/search")
    public ResponseEntity<List<MockFlightResponse>> searchFlights(@RequestParam String departureAirportCode,
                                                                  @RequestParam String arrivalAirportCode,
                                                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departureDate) {
        return ResponseEntity.ok(mockFlightService.searchFlights(departureAirportCode, arrivalAirportCode, departureDate));
    }

    @GetMapping
    public ResponseEntity<List<MockFlightResponse>> getFlights() {
        return ResponseEntity.ok(mockFlightService.readFlights());
    }
}
