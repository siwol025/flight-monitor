package com.siwol025.flight_monitor.monitor.service;

import com.siwol025.flight_monitor.global.exception.custom.NotFoundException;
import com.siwol025.flight_monitor.monitor.repository.FlightLatestPriceInfoRepository;
import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import com.siwol025.flight_monitor.subscription.service.FlightService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class FlightLatestPriceInfoServiceTest {

    @Mock
    private FlightLatestPriceInfoRepository flightLatestPriceInfoRepository;

    @Mock
    private FlightService flightService;

    @InjectMocks
    private FlightLatestPriceInfoService subscriptionService;

    @Test
    void updateLatestPriceInfo_이전가격없으면_NotFoundException() {
        // given
        given(flightLatestPriceInfoRepository.findByFlightIdAndSeatGrade(1L, SeatGrade.ECONOMY))
                .willReturn(Optional.empty());

        // when
        NotFoundException ex = catchThrowableOfType(
                () -> subscriptionService.updateLatestPriceInfo(1L, SeatGrade.ECONOMY, BigDecimal.valueOf(100_000)),
                NotFoundException.class
        );

        // then
        assertThat(ex).isNotNull();
        assertThat(ex.getStatus().value()).isEqualTo(404);
    }
}
