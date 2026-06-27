package com.siwol025.flight_monitor.subscription.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

import com.siwol025.flight_monitor.global.exception.ErrorTag;
import com.siwol025.flight_monitor.global.exception.custom.NotFoundException;
import com.siwol025.flight_monitor.subscription.repository.FlightRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FlightServiceTest {

    @Mock private FlightRepository flightRepository;
    @InjectMocks private FlightService flightService;

    @Test
    void getFlight_존재하지않는ID_NotFoundException_FLIGHT_NOT_FOUND() {
        given(flightRepository.findByFlightId(99L)).willReturn(Optional.empty());

        NotFoundException ex = assertThrows(NotFoundException.class,
            () -> flightService.getFlight(99L));

        assertThat(ex.getErrorTag()).isEqualTo(ErrorTag.FLIGHT_NOT_FOUND);
        assertThat(ex.getStatus().value()).isEqualTo(404);
    }
}
