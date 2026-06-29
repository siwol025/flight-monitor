package com.siwol025.flight_monitor.mock.flight.service;

import com.siwol025.flight_monitor.global.exception.ErrorTag;
import com.siwol025.flight_monitor.global.exception.custom.BadRequestException;
import com.siwol025.flight_monitor.global.exception.custom.NotFoundException;
import com.siwol025.flight_monitor.mock.airline.domain.MockAirline;
import com.siwol025.flight_monitor.mock.airport.domain.MockAirport;
import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import com.siwol025.flight_monitor.mock.airline.repository.MockAirlineRepository;
import com.siwol025.flight_monitor.mock.airport.repository.MockAirportRepository;
import com.siwol025.flight_monitor.mock.flight.domain.MockFlight;
import com.siwol025.flight_monitor.mock.flight.dto.request.MockFlightRequest;
import com.siwol025.flight_monitor.mock.flight.dto.request.MockFlightUpdateRequest;
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
        validateFlight(request.flightNumber(), request.departureTime());
        MockAirline mockAirline = readAirline(request.airlineCode());
        MockAirport departureMockAirport = readAirport(request.departureAirportCode());
        MockAirport arrivalMockAirport = readAirport(request.arrivalAirportCode());

        MockFlight saved = mockFlightRepository.save(request.toFlight(mockAirline, departureMockAirport, arrivalMockAirport));
        return saved.getId();
    }

    @Transactional
    public void updateFlight(Long id, MockFlightUpdateRequest request) {
        validateFlight(request.flightNumber(), request.departureTime());
        MockFlight mockFlight = mockFlightRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorTag.FLIGHT_NOT_FOUND));

        mockFlight.updateFlightInfo(request.flightNumber(), request.departureTime(), request.arrivalTime());
    }

    @Transactional
    public void deleteFlight(Long id) {
        MockFlight mockFlight = mockFlightRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorTag.FLIGHT_NOT_FOUND));

        mockFlightRepository.delete(mockFlight);
    }

    public List<MockFlightResponse> searchFlights(String departureAirportCode, String arrivalAirportCode, LocalDate departureDate, SeatGrade seatGrade) {
        LocalDateTime startOfDay = departureDate.atStartOfDay();
        LocalDateTime endOfDay = departureDate.atTime(LocalTime.MAX);

        return mockFlightRepository.findFlightsWithDetails(departureAirportCode, arrivalAirportCode, startOfDay, endOfDay, seatGrade)
                .stream()
                .map(flight -> MockFlightResponse.of(flight, seatGrade))
                .toList();
    }

    public List<MockFlightResponse> readFlights() {
        return mockFlightRepository.findAllWithPrices()
                .stream()
                .map(MockFlightResponse::from)
                .toList();
    }

    public MockFlightResponse readFlight(Long flightId) {
        try {
            // 실제 외부 항공사 API 호출 시 발생하는 네트워크 레이턴시 시뮬레이션
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        MockFlight mockFlight = mockFlightRepository.findById(flightId)
                .orElseThrow(() -> new NotFoundException(ErrorTag.FLIGHT_NOT_FOUND));

        return MockFlightResponse.from(mockFlight);
    }

    public void validateFlight(String flightNumber, LocalDateTime departureTime) {
        if (isDuplicateFlight(flightNumber, departureTime)) {
            throw new BadRequestException(ErrorTag.DUPLICATE_FLIGHT);
        }
    }

    public boolean isDuplicateFlight(String flightNumber, LocalDateTime departureTime) {
        LocalDate requestDate = departureTime.toLocalDate();
        LocalDateTime startOfDay = requestDate.atStartOfDay();
        LocalDateTime endOfDay = requestDate.atTime(LocalTime.MAX);

        return mockFlightRepository.existsByFlightNumberAndDepartureTimeBetween(flightNumber, startOfDay, endOfDay);
    }

    public MockAirline readAirline(String code) {
        return mockAirlineRepository.findByAirlineCode(code)
                .orElseThrow(() -> new NotFoundException(ErrorTag.AIRLINE_NOT_FOUND));
    }

    public MockAirport readAirport(String code) {
        return mockAirportRepository.findByAirportCode(code)
                .orElseThrow(() -> new NotFoundException(ErrorTag.AIRPORT_NOT_FOUND));
    }
}
