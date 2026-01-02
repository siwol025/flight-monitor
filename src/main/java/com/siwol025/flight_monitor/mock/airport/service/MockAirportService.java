package com.siwol025.flight_monitor.mock.airport.service;

import com.siwol025.flight_monitor.domain.airport.Airport;
import com.siwol025.flight_monitor.mock.airport.dto.request.MockAirportRequest;
import com.siwol025.flight_monitor.mock.airport.dto.response.MockAirportResponse;
import com.siwol025.flight_monitor.mock.airport.repository.MockAirportRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MockAirportService {
    private final MockAirportRepository mockAirportRepository;

    @Transactional(readOnly = true)
    public List<MockAirportResponse> getAirports() {
        return mockAirportRepository.findAll().stream()
                .map(MockAirportResponse::of)
                .collect(Collectors.toList());
    }

    public Long createAirport(MockAirportRequest request) {
        Airport savedAirport = mockAirportRepository.save(request.toAirport());
        return savedAirport.getId();
    }
}
