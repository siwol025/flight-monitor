package com.siwol025.flight_monitor.mock.airline.service;

import com.siwol025.flight_monitor.domain.airline.Airline;
import com.siwol025.flight_monitor.mock.airline.dto.request.MockAirlineRequest;
import com.siwol025.flight_monitor.mock.airline.dto.response.MockAirlineResponse;
import com.siwol025.flight_monitor.mock.airline.repository.MockAirlineRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MockAirlineService {
    private final MockAirlineRepository mockAirlineRepository;

    public List<MockAirlineResponse> getAirlines() {
        return mockAirlineRepository.findAll().stream()
                .map(MockAirlineResponse::of)
                .toList();
    }

    @Transactional
    public Long createAirline(MockAirlineRequest request) {
        Airline savedAirline = mockAirlineRepository.save(request.toAirline());
        return savedAirline.getId();
    }

    @Transactional
    public void deleteAirline(String code) {
        Airline airline = mockAirlineRepository.findByAirlineCode(code)
                .orElseThrow(() -> new IllegalArgumentException("해당 항공사 데이터를 찾을 수 없습니다."));

        mockAirlineRepository.delete(airline);
    }
}
