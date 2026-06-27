package com.siwol025.flight_monitor.mock.airline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

import com.siwol025.flight_monitor.global.exception.ErrorTag;
import com.siwol025.flight_monitor.global.exception.custom.NotFoundException;
import com.siwol025.flight_monitor.mock.airline.repository.MockAirlineRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MockAirlineServiceTest {

    @Mock private MockAirlineRepository mockAirlineRepository;

    @InjectMocks private MockAirlineService mockAirlineService;

    @Test
    void deleteAirline_존재하지않는코드_NotFoundException_AIRLINE_NOT_FOUND() {
        given(mockAirlineRepository.findByAirlineCode("UNKNOWN")).willReturn(Optional.empty());

        NotFoundException ex = assertThrows(
                NotFoundException.class,
                () -> mockAirlineService.deleteAirline("UNKNOWN")
        );

        assertThat(ex.getErrorTag()).isEqualTo(ErrorTag.AIRLINE_NOT_FOUND);
        assertThat(ex.getStatus().value()).isEqualTo(404);
    }
}
