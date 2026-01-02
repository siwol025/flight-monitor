package com.siwol025.flight_monitor.mock.flight.service;

import com.siwol025.flight_monitor.domain.airline.Airline;
import com.siwol025.flight_monitor.domain.airport.Airport;
import com.siwol025.flight_monitor.domain.flight.Flight;
import com.siwol025.flight_monitor.mock.airline.repository.MockAirlineRepository;
import com.siwol025.flight_monitor.mock.airport.repository.MockAirportRepository;
import com.siwol025.flight_monitor.mock.flight.dto.request.MockFlightRequest;
import com.siwol025.flight_monitor.mock.flight.repository.MockFlightRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MockFlightService {
    private final MockFlightRepository mockFlightRepository;
    private final MockAirlineRepository mockAirlineRepository;
    private final MockAirportRepository mockAirportRepository;

    @Transactional
    public Long createFlight(MockFlightRequest request) {
        Airline airline = readAirline(request.airlineCode());
        Airport departureAirport = readAirport(request.departureAirportCode());
        Airport arrivalAirport = readAirport(request.arrivalAirportCode());

        Flight saved = mockFlightRepository.save(request.toFlight(airline, departureAirport, arrivalAirport));
        return saved.getId();
    }

    public Airline readAirline(String code) {
        return mockAirlineRepository.findByAirlineCode(code)
                .orElseThrow(() -> new IllegalArgumentException("해당 항공사를 찾을 수 없습니다."));
    }

    public Airport readAirport(String code) {
        return mockAirportRepository.findByAirportCode(code)
                .orElseThrow(() -> new IllegalArgumentException("해당 공항을 찾을 수 없습니다."));
    }
}
