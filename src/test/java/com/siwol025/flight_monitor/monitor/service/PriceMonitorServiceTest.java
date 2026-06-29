package com.siwol025.flight_monitor.monitor.service;

import com.siwol025.flight_monitor.mock.flight.dto.response.MockFlightResponse;
import com.siwol025.flight_monitor.mock.flight.dto.response.MockFlightSeatPriceResponse;
import com.siwol025.flight_monitor.monitor.dto.PriceDropNotificationDto;
import com.siwol025.flight_monitor.monitor.producer.NotificationPublisher;
import com.siwol025.flight_monitor.monitor.utils.FlightPriceCacheManager;
import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import com.siwol025.flight_monitor.subscription.dto.FlightMonitorTaskDto;
import com.siwol025.flight_monitor.subscription.service.FlightDataProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class PriceMonitorServiceTest {

    @Mock
    private FlightDataProvider flightDataProvider;

    @Mock
    private NotificationPublisher producer;

    @Mock
    private FlightLatestPriceInfoService flightLatestPriceInfoService;

    @Mock
    private FlightPriceCacheManager cacheManager;

    @InjectMocks
    private PriceMonitorService priceMonitorService;

    private final FlightMonitorTaskDto taskDto = new FlightMonitorTaskDto(1L, SeatGrade.ECONOMY);

    private MockFlightResponse mockFlightResponse(BigDecimal price) {
        return MockFlightResponse.builder()
                .id(1L)
                .flightNumber("KE101")
                .airlineCode("KE")
                .departureAirportCode("ICN")
                .arrivalAirportCode("NRT")
                .departureTime(LocalDateTime.of(2026, 7, 1, 10, 0))
                .arrivalTime(LocalDateTime.of(2026, 7, 1, 12, 0))
                .isSeatAvailable(true)
                .seatPrices(List.of(new MockFlightSeatPriceResponse("KE101", SeatGrade.ECONOMY, price)))
                .build();
    }

    @Test
    void checkPriceAndNotify_API_null_응답이면_조기리턴_알림없음() {
        // given
        given(flightDataProvider.fetchMockFlight(1L)).willReturn(null);

        // when
        priceMonitorService.checkPriceAndNotify(taskDto);

        // then
        then(producer).shouldHaveNoInteractions();
    }

    @Test
    void checkPriceAndNotify_캐시가격과_현재가격이_같으면_알림없음() {
        // given
        BigDecimal price = BigDecimal.valueOf(100_000);
        given(flightDataProvider.fetchMockFlight(1L)).willReturn(mockFlightResponse(price));
        given(cacheManager.getPreviousPrice(1L, SeatGrade.ECONOMY, price)).willReturn(price);

        // when
        priceMonitorService.checkPriceAndNotify(taskDto);

        // then
        then(producer).shouldHaveNoInteractions();
    }

    @Test
    void checkPriceAndNotify_StaleCace_감지시_캐시만갱신_알림없음() {
        // given
        BigDecimal currentPrice = BigDecimal.valueOf(100_000);
        BigDecimal staleCachePrice = BigDecimal.valueOf(80_000);
        given(flightDataProvider.fetchMockFlight(1L)).willReturn(mockFlightResponse(currentPrice));
        given(cacheManager.getPreviousPrice(1L, SeatGrade.ECONOMY, currentPrice)).willReturn(staleCachePrice);
        given(flightLatestPriceInfoService.getPreviousPrice(1L, SeatGrade.ECONOMY))
                .willReturn(Optional.of(currentPrice));

        // when
        priceMonitorService.checkPriceAndNotify(taskDto);

        // then
        then(cacheManager).should().saveToCache(eq(1L), eq(SeatGrade.ECONOMY), eq(BigDecimal.valueOf(100_000)));
        then(producer).shouldHaveNoInteractions();
    }

    @Test
    void checkPriceAndNotify_가격하락시_publishPriceDrop_호출됨() {
        // given
        BigDecimal currentPrice = BigDecimal.valueOf(80_000);
        BigDecimal previousPrice = BigDecimal.valueOf(100_000);
        given(flightDataProvider.fetchMockFlight(1L)).willReturn(mockFlightResponse(currentPrice));
        given(cacheManager.getPreviousPrice(1L, SeatGrade.ECONOMY, currentPrice)).willReturn(previousPrice);
        given(flightLatestPriceInfoService.getPreviousPrice(1L, SeatGrade.ECONOMY))
                .willReturn(Optional.of(previousPrice));

        // when
        priceMonitorService.checkPriceAndNotify(taskDto);

        // then
        then(producer).should().publishPriceDrop(any(PriceDropNotificationDto.class));
    }

    @Test
    void checkPriceAndNotify_가격상승시_DB업데이트_알림없음() {
        // given
        BigDecimal currentPrice = BigDecimal.valueOf(120_000);
        BigDecimal previousPrice = BigDecimal.valueOf(100_000);
        given(flightDataProvider.fetchMockFlight(1L)).willReturn(mockFlightResponse(currentPrice));
        given(cacheManager.getPreviousPrice(1L, SeatGrade.ECONOMY, currentPrice)).willReturn(previousPrice);
        given(flightLatestPriceInfoService.getPreviousPrice(1L, SeatGrade.ECONOMY))
                .willReturn(Optional.of(previousPrice));

        // when
        priceMonitorService.checkPriceAndNotify(taskDto);

        // then
        then(flightLatestPriceInfoService).should().updateLatestPriceInfo(1L, SeatGrade.ECONOMY, currentPrice);
        then(producer).shouldHaveNoInteractions();
    }

    @Test
    void checkPriceAndNotify_saveToCache_도메인타입으로_호출된다() {
        BigDecimal currentPrice = BigDecimal.valueOf(80_000);
        BigDecimal previousPrice = BigDecimal.valueOf(100_000);
        given(flightDataProvider.fetchMockFlight(1L)).willReturn(mockFlightResponse(currentPrice));
        given(cacheManager.getPreviousPrice(1L, SeatGrade.ECONOMY, currentPrice)).willReturn(previousPrice);
        given(flightLatestPriceInfoService.getPreviousPrice(1L, SeatGrade.ECONOMY))
                .willReturn(Optional.of(previousPrice));

        priceMonitorService.checkPriceAndNotify(taskDto);

        // 도메인 타입 시그니처로 saveToCache가 호출되어야 함
        then(cacheManager).should().saveToCache(eq(1L), eq(SeatGrade.ECONOMY), eq(BigDecimal.valueOf(80_000)));
    }

    @Test
    void PriceMonitorService는_FlightDataProvider_인터페이스에_의존해야한다() {
        // PriceMonitorService의 선언된 필드 중 FlightDataProvider 타입이 있는지 검사
        boolean hasFlightDataProviderField = Arrays.stream(PriceMonitorService.class.getDeclaredFields())
                .anyMatch(f -> f.getType().equals(FlightDataProvider.class));
        assertThat(hasFlightDataProviderField).isTrue();
    }
}
