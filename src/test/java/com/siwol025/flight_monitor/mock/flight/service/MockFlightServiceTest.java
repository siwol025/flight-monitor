package com.siwol025.flight_monitor.mock.flight.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.siwol025.flight_monitor.global.exception.ErrorTag;
import com.siwol025.flight_monitor.global.exception.custom.BadRequestException;
import com.siwol025.flight_monitor.global.exception.custom.NotFoundException;
import com.siwol025.flight_monitor.mock.flight.repository.MockFlightRepository;
import com.siwol025.flight_monitor.mock.airline.repository.MockAirlineRepository;
import com.siwol025.flight_monitor.mock.airport.repository.MockAirportRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MockFlightServiceTest {

    @Mock private MockFlightRepository mockFlightRepository;
    @Mock private MockAirlineRepository mockAirlineRepository;
    @Mock private MockAirportRepository mockAirportRepository;

    @InjectMocks
    private MockFlightService mockFlightService;

    @Test
    void readFlight_존재하지않는ID_NotFoundException_FLIGHT_NOT_FOUND() {
        given(mockFlightRepository.findById(999L)).willReturn(Optional.empty());

        NotFoundException ex = assertThrows(
                NotFoundException.class,
                () -> mockFlightService.readFlight(999L)
        );

        assertThat(ex.getErrorTag()).isEqualTo(ErrorTag.FLIGHT_NOT_FOUND);
        assertThat(ex.getStatus().value()).isEqualTo(404);
    }

    @Test
    void validateFlight_중복시_BadRequestException_DUPLICATE_FLIGHT_발생() {
        given(mockFlightRepository.existsByFlightNumberAndDepartureTimeBetween(
                anyString(), any(), any())).willReturn(true);

        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> mockFlightService.validateFlight("KE001", LocalDateTime.now())
        );

        assertThat(ex.getErrorTag()).isEqualTo(ErrorTag.DUPLICATE_FLIGHT);
    }
}
