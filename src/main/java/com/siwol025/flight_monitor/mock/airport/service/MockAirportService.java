package com.siwol025.flight_monitor.mock.airport.service;

import com.siwol025.flight_monitor.global.exception.ErrorTag;
import com.siwol025.flight_monitor.global.exception.custom.NotFoundException;
import com.siwol025.flight_monitor.mock.airport.domain.MockAirport;
import com.siwol025.flight_monitor.mock.airport.dto.request.MockAirportRequest;
import com.siwol025.flight_monitor.mock.airport.dto.response.MockAirportResponse;
import com.siwol025.flight_monitor.mock.airport.repository.MockAirportRepository;
import java.util.List;
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
        MockAirport savedMockAirport = mockAirportRepository.save(request.toAirport());
        return savedMockAirport.getId();
    }

    @Transactional
    public MockAirportResponse updateAirport(MockAirportRequest request) {
        MockAirport mockAirport = mockAirportRepository.findByAirportCode(request.code())
                .orElseThrow(() -> new NotFoundException(ErrorTag.AIRPORT_NOT_FOUND));

        mockAirport.updateName(request.name());
        return MockAirportResponse.of(mockAirport);
    }

    @Transactional
    public void deleteAirport(String code) {
        MockAirport mockAirport = mockAirportRepository.findByAirportCode(code)
                .orElseThrow(() -> new NotFoundException(ErrorTag.AIRPORT_NOT_FOUND));

        mockAirportRepository.delete(mockAirport);
    }
}
