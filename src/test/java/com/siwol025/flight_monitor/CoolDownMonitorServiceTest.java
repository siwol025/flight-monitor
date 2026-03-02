package com.siwol025.flight_monitor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.siwol025.flight_monitor.mock.flight.dto.response.MockFlightResponse;
import com.siwol025.flight_monitor.monitor.service.PriceMonitorService;
import com.siwol025.flight_monitor.monitor.service.FlightSeatGradePriceService;
import com.siwol025.flight_monitor.monitor.utils.DynamicTtlCalculator;
import com.siwol025.flight_monitor.subscription.domain.flight.Flight;
import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import com.siwol025.flight_monitor.subscription.service.FlightFetcher;
import com.siwol025.flight_monitor.subscription.service.FlightService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class CoolDownMonitorServiceTest {

    @InjectMocks
    private PriceMonitorService priceMonitorService;

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private FlightFetcher flightFetcher;
    @Mock private FlightService flightService;
    @Mock private FlightSeatGradePriceService flightSeatGradePriceService;
    @Mock private DynamicTtlCalculator ttlCalculator;

    @Test
    @DisplayName("쿨다운 키가 존재하면 외부 API를 호출하지 않고 종료한다.")
    void skipApiCallWhenCooldownKeyExists() {
        // given
        Long flightId = 1L;
        SeatGrade seatGrade = SeatGrade.ECONOMY;
        given(redisTemplate.hasKey("flight:api_cooldown:" + flightId)).willReturn(true);

        // when
        priceMonitorService.checkAndUpdatePrice(flightId, seatGrade);

        // then: 팩트 검증 - 외부 API 호출 및 후속 로직이 전혀 실행되지 않아야 함
        verify(flightFetcher, never()).fetchMockFlight(anyLong());
        verify(flightService, never()).getFlight(anyLong());
        verify(flightSeatGradePriceService, never()).processPublishAndPriceUpdate(any(), any(), any(), any(), anyString());
    }

    @Test
    @DisplayName("쿨다운 키가 없고 가격 변동이 없으면 쿨다운만 갱신하고 DB 업데이트는 위임하지 않는다.")
    void updateCooldownButSkipDbUpdateWhenPriceIsUnchanged() {
        // given
        Long flightId = 1L;
        SeatGrade seatGrade = SeatGrade.ECONOMY;
        BigDecimal currentPrice = new BigDecimal("50000");
        String redisPriceKey = "flight:price:" + flightId + ":" + seatGrade.name();

        given(redisTemplate.hasKey("flight:api_cooldown:" + flightId)).willReturn(false);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        MockFlightResponse mockResponse = mock(MockFlightResponse.class);
        given(mockResponse.getPriceBySeatClass(seatGrade)).willReturn(currentPrice);
        given(flightFetcher.fetchMockFlight(flightId)).willReturn(mockResponse);

        Flight mockFlight = mock(Flight.class);
        given(mockFlight.getDepartureTime()).willReturn(LocalDateTime.now().plusDays(10));
        given(flightService.getFlight(flightId)).willReturn(mockFlight);

        given(ttlCalculator.calculateCooldownTtl(any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(Duration.ofHours(6));

        // 기존에 캐시된 가격이 외부 API 조회 가격과 동일한 상황 모사
        given(valueOperations.get(redisPriceKey)).willReturn("50000");

        // when
        priceMonitorService.checkAndUpdatePrice(flightId, seatGrade);

        // then: 팩트 검증
        verify(flightFetcher).fetchMockFlight(flightId); // 1. API 호출됨
        verify(valueOperations).set(eq("flight:api_cooldown:" + flightId), eq("LOCKED"), any(Duration.class)); // 2. 쿨다운 갱신됨
        verify(flightSeatGradePriceService, never()).processPublishAndPriceUpdate(any(), any(), any(), any(), anyString()); // 3. 가격이 같으므로 DB 로직 미실행
    }

    @Test
    @DisplayName("가격 변동이 발생하면 외부 API 위임 업데이트 로직이 정상 호출된다.")
    void callDbUpdateWhenPriceIsChanged() {
        // given
        Long flightId = 1L;
        SeatGrade seatGrade = SeatGrade.ECONOMY;
        BigDecimal currentPrice = new BigDecimal("40000"); // 하락한 가격
        String redisPriceKey = "flight:price:" + flightId + ":" + seatGrade.name();

        given(redisTemplate.hasKey("flight:api_cooldown:" + flightId)).willReturn(false);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        MockFlightResponse mockResponse = mock(MockFlightResponse.class);
        given(mockResponse.getPriceBySeatClass(seatGrade)).willReturn(currentPrice);
        given(flightFetcher.fetchMockFlight(flightId)).willReturn(mockResponse);

        Flight mockFlight = mock(Flight.class);
        given(mockFlight.getDepartureTime()).willReturn(LocalDateTime.now().plusDays(10));
        given(flightService.getFlight(flightId)).willReturn(mockFlight);

        given(ttlCalculator.calculateCooldownTtl(any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(Duration.ofHours(6));

        // 기존에 캐시된 가격이 외부 API 조회 가격과 다른 상황 모사 (기존: 50000, 현재: 40000)
        given(valueOperations.get(redisPriceKey)).willReturn("50000");

        // when
        priceMonitorService.checkAndUpdatePrice(flightId, seatGrade);

        // then: 팩트 검증
        verify(valueOperations).set(eq("flight:api_cooldown:" + flightId), eq("LOCKED"), any(Duration.class));
        verify(flightSeatGradePriceService).processPublishAndPriceUpdate(eq(mockFlight), eq(seatGrade), eq(currentPrice), eq(mockResponse), eq(redisPriceKey));
    }
}
