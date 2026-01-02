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
@Transactional(readOnly = true)
public class MockAirportService {
    private final MockAirportRepository mockAirportRepository;

    public List<MockAirportResponse> getAirports() {
        return mockAirportRepository.findAll().stream()
                .map(MockAirportResponse::of)
                .toList();
    }

    @Transactional
    public Long createAirport(MockAirportRequest request) {
        Airport savedAirport = mockAirportRepository.save(request.toAirport());
        return savedAirport.getId();
    }

    @Transactional
    public MockAirportResponse updateAirport(MockAirportRequest request) {
        Airport airport = mockAirportRepository.findByAirportCode(request.code())
                .orElseThrow(() -> new IllegalArgumentException("해당 공항데이터를 찾을 수 없습니다."));

        airport.updateName(request.name());
        return MockAirportResponse.of(airport);
    }

    @Transactional
    public void deleteAirport(String code) {
        Airport airport = mockAirportRepository.findByAirportCode(code)
                .orElseThrow(() -> new IllegalArgumentException("해당 공항데이터를 찾을 수 없습니다."));

        mockAirportRepository.delete(airport);
    }
}
