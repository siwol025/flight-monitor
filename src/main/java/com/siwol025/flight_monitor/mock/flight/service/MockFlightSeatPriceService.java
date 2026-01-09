package com.siwol025.flight_monitor.mock.flight.service;

import com.siwol025.flight_monitor.domain.flight.Flight;
import com.siwol025.flight_monitor.domain.flight.FlightSeatPrice;
import com.siwol025.flight_monitor.mock.flight.dto.request.MockFlightSeatPriceRequest;
import com.siwol025.flight_monitor.mock.flight.dto.response.MockFlightSeatPriceResponse;
import com.siwol025.flight_monitor.mock.flight.repository.MockFlightRepository;
import com.siwol025.flight_monitor.mock.flight.repository.MockFlightSeatPriceRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MockFlightSeatPriceService {
    private final MockFlightSeatPriceRepository mockFlightSeatPriceRepository;
    private final MockFlightRepository mockFlightRepository;

    @Transactional
    public void upsertPrice(MockFlightSeatPriceRequest request) {
        Flight flight = mockFlightRepository.findById(request.flightId())
                .orElseThrow(() -> new IllegalArgumentException("해당 항공편을 찾을 수 없습니다."));

        Optional<FlightSeatPrice> existingPrice = mockFlightSeatPriceRepository.findByFlightIdAndSeatGrade(
                request.flightId(), request.seatGrade());

        if (existingPrice.isPresent()) {
            existingPrice.get().updatePrice(request.price());
        } else {
            FlightSeatPrice newPrice = FlightSeatPrice.builder()
                    .seatGrade(request.seatGrade())
                    .price(request.price())
                    .flight(flight)
                    .build();

            flight.addFlightSeatPrice(newPrice);
            mockFlightSeatPriceRepository.save(newPrice);
        }
    }

    public List<MockFlightSeatPriceResponse> readSeatPrices() {
        return mockFlightSeatPriceRepository.findAllWithFlightNumber().stream()
                .map(MockFlightSeatPriceResponse::of)
                .toList();
    }
}
