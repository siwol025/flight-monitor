package com.siwol025.flight_monitor.mock.flight.service;

import com.siwol025.flight_monitor.domain.airline.Airline;
import com.siwol025.flight_monitor.domain.airport.Airport;
import com.siwol025.flight_monitor.domain.flight.Flight;
import com.siwol025.flight_monitor.mock.airline.repository.MockAirlineRepository;
import com.siwol025.flight_monitor.mock.airport.repository.MockAirportRepository;
import com.siwol025.flight_monitor.mock.flight.dto.request.MockFlightRequest;
import com.siwol025.flight_monitor.mock.flight.dto.response.MockFlightResponse;
import com.siwol025.flight_monitor.mock.flight.repository.MockFlightRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
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
        validateFlight(request);
        Airline airline = readAirline(request.airlineCode());
        Airport departureAirport = readAirport(request.departureAirportCode());
        Airport arrivalAirport = readAirport(request.arrivalAirportCode());

        Flight saved = mockFlightRepository.save(request.toFlight(airline, departureAirport, arrivalAirport));
        return saved.getId();
    }

    public List<MockFlightResponse> searchFlights(String departureAirportCode, String arrivalAirportCode, LocalDate departureDate) {
        LocalDateTime startOfDay = departureDate.atStartOfDay();
        LocalDateTime endOfDay = departureDate.atTime(LocalTime.MAX);

        return mockFlightRepository.findFlightsWithDetails(departureAirportCode, arrivalAirportCode, startOfDay, endOfDay)
                .stream()
                .map(MockFlightResponse::of)
                .toList();
    }

    public List<MockFlightResponse> readFlights() {
        return mockFlightRepository.findAll()
                .stream()
                .map(MockFlightResponse::of)
                .toList();
    }

    public MockFlightResponse readFlight(Long flightId) {
        Flight flight = mockFlightRepository.findById(flightId)
                .orElseThrow(() -> new IllegalArgumentException("해당 항공편을 찾을 수 없습니다."));

        return MockFlightResponse.of(flight);
    }

    public void validateFlight(MockFlightRequest request) {
        if (isDuplicateFlight(request)) {
            throw new IllegalArgumentException(
                    String.format("이미 해당 날짜(%s)에 등록된 항공편(%s)이 존재합니다.",
                            request.departureTime(), request.flightNumber())
            );
        }
    }

    public boolean isDuplicateFlight(MockFlightRequest request) {
        LocalDate requestDate = request.departureTime().toLocalDate();
        LocalDateTime startOfDay = requestDate.atStartOfDay();
        LocalDateTime endOfDay = requestDate.atTime(LocalTime.MAX);

        return mockFlightRepository.existsByFlightNumberAndDepartureTimeBetween(request.flightNumber(), startOfDay, endOfDay);
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
