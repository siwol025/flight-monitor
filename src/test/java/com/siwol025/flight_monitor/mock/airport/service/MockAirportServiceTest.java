package com.siwol025.flight_monitor.mock.airport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

import com.siwol025.flight_monitor.global.exception.ErrorTag;
import com.siwol025.flight_monitor.global.exception.custom.NotFoundException;
import com.siwol025.flight_monitor.mock.airport.dto.request.MockAirportRequest;
import com.siwol025.flight_monitor.mock.airport.repository.MockAirportRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MockAirportServiceTest {

    @Mock private MockAirportRepository mockAirportRepository;

    @InjectMocks private MockAirportService mockAirportService;

    @Test
    void updateAirport_존재하지않는코드_NotFoundException_AIRPORT_NOT_FOUND() {
        MockAirportRequest request = new MockAirportRequest("UNKNOWN", "없는공항");
        given(mockAirportRepository.findByAirportCode("UNKNOWN")).willReturn(Optional.empty());

        NotFoundException ex = assertThrows(
                NotFoundException.class,
                () -> mockAirportService.updateAirport(request)
        );

        assertThat(ex.getErrorTag()).isEqualTo(ErrorTag.AIRPORT_NOT_FOUND);
        assertThat(ex.getStatus().value()).isEqualTo(404);
    }

    @Test
    void deleteAirport_존재하지않는코드_NotFoundException_AIRPORT_NOT_FOUND() {
        given(mockAirportRepository.findByAirportCode("UNKNOWN")).willReturn(Optional.empty());

        NotFoundException ex = assertThrows(
                NotFoundException.class,
                () -> mockAirportService.deleteAirport("UNKNOWN")
        );

        assertThat(ex.getErrorTag()).isEqualTo(ErrorTag.AIRPORT_NOT_FOUND);
        assertThat(ex.getStatus().value()).isEqualTo(404);
    }
}
