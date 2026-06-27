package com.siwol025.flight_monitor.mock.airline.service;

import com.siwol025.flight_monitor.global.exception.ErrorTag;
import com.siwol025.flight_monitor.global.exception.custom.NotFoundException;
import com.siwol025.flight_monitor.mock.airline.domain.MockAirline;
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
        MockAirline savedMockAirline = mockAirlineRepository.save(request.toAirline());
        return savedMockAirline.getId();
    }

    @Transactional
    public void deleteAirline(String code) {
        MockAirline mockAirline = mockAirlineRepository.findByAirlineCode(code)
                .orElseThrow(() -> new NotFoundException(ErrorTag.AIRLINE_NOT_FOUND));

        mockAirlineRepository.delete(mockAirline);
    }
}
