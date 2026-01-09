package com.siwol025.flight_monitor.mock.flight.controller;

import com.siwol025.flight_monitor.mock.flight.dto.request.MockFlightSeatPriceRequest;
import com.siwol025.flight_monitor.mock.flight.dto.response.MockFlightSeatPriceResponse;
import com.siwol025.flight_monitor.mock.flight.service.MockFlightSeatPriceService;
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
@RequestMapping("/external/api/prices")
public class MockFlightSeatPriceController implements MockFlightSeatPriceControllerSwagger{
    private final MockFlightSeatPriceService mockFlightSeatPriceService;

    @PostMapping
    public ResponseEntity<Void> upsertPrice(@Valid @RequestBody MockFlightSeatPriceRequest request) {
        mockFlightSeatPriceService.upsertPrice(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<MockFlightSeatPriceResponse>> getSeatPrices() {
        return ResponseEntity.ok(mockFlightSeatPriceService.readSeatPrices());
    }
}
