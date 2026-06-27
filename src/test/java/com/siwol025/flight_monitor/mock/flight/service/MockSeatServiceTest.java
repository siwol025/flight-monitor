package com.siwol025.flight_monitor.mock.flight.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

import com.siwol025.flight_monitor.global.exception.ErrorTag;
import com.siwol025.flight_monitor.global.exception.custom.NotFoundException;
import com.siwol025.flight_monitor.mock.flight.dto.request.MockSeatBulkRequest;
import com.siwol025.flight_monitor.mock.flight.repository.MockFlightRepository;
import com.siwol025.flight_monitor.mock.flight.repository.MockSeatRepository;
import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MockSeatServiceTest {

    @Mock private MockSeatRepository mockSeatRepository;
    @Mock private MockFlightRepository mockFlightRepository;

    @InjectMocks
    private MockSeatService mockSeatService;

    @Test
    void createBulkSeats_존재하지않는flightId_NotFoundException_FLIGHT_NOT_FOUND() {
        given(mockFlightRepository.findById(999L)).willReturn(Optional.empty());

        MockSeatBulkRequest request = new MockSeatBulkRequest(SeatGrade.ECONOMY, 1, 3, "ABC");

        NotFoundException ex = assertThrows(
                NotFoundException.class,
                () -> mockSeatService.createBulkSeats(999L, request)
        );

        assertThat(ex.getErrorTag()).isEqualTo(ErrorTag.FLIGHT_NOT_FOUND);
        assertThat(ex.getStatus().value()).isEqualTo(404);
    }

    @Test
    void reserveSeat_존재하지않는seatId_NotFoundException_SEAT_NOT_FOUND() {
        given(mockSeatRepository.findById(999L)).willReturn(Optional.empty());

        NotFoundException ex = assertThrows(
                NotFoundException.class,
                () -> mockSeatService.reserveSeat(999L)
        );

        assertThat(ex.getErrorTag()).isEqualTo(ErrorTag.SEAT_NOT_FOUND);
        assertThat(ex.getStatus().value()).isEqualTo(404);
    }

    @Test
    void cancelSeat_존재하지않는seatId_NotFoundException_SEAT_NOT_FOUND() {
        given(mockSeatRepository.findById(999L)).willReturn(Optional.empty());

        NotFoundException ex = assertThrows(
                NotFoundException.class,
                () -> mockSeatService.cancelSeat(999L)
        );

        assertThat(ex.getErrorTag()).isEqualTo(ErrorTag.SEAT_NOT_FOUND);
        assertThat(ex.getStatus().value()).isEqualTo(404);
    }
}
