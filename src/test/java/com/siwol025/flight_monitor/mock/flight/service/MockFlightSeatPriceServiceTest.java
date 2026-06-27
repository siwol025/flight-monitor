package com.siwol025.flight_monitor.mock.flight.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

import com.siwol025.flight_monitor.global.exception.ErrorTag;
import com.siwol025.flight_monitor.global.exception.custom.NotFoundException;
import com.siwol025.flight_monitor.mock.flight.dto.request.MockFlightSeatPriceRequest;
import com.siwol025.flight_monitor.mock.flight.repository.MockFlightRepository;
import com.siwol025.flight_monitor.mock.flight.repository.MockFlightSeatPriceRepository;
import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MockFlightSeatPriceServiceTest {

    @Mock private MockFlightSeatPriceRepository mockFlightSeatPriceRepository;
    @Mock private MockFlightRepository mockFlightRepository;

    @InjectMocks
    private MockFlightSeatPriceService mockFlightSeatPriceService;

    @Test
    void upsertPrice_존재하지않는flightId_NotFoundException_FLIGHT_NOT_FOUND() {
        MockFlightSeatPriceRequest request = new MockFlightSeatPriceRequest(999L, SeatGrade.ECONOMY, BigDecimal.valueOf(150000));

        given(mockFlightRepository.findById(999L)).willReturn(Optional.empty());

        NotFoundException ex = assertThrows(
                NotFoundException.class,
                () -> mockFlightSeatPriceService.upsertPrice(request)
        );

        assertThat(ex.getErrorTag()).isEqualTo(ErrorTag.FLIGHT_NOT_FOUND);
        assertThat(ex.getStatus().value()).isEqualTo(404);
    }
}
