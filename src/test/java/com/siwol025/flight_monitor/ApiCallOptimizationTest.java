package com.siwol025.flight_monitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

import com.siwol025.flight_monitor.mock.flight.dto.response.MockFlightResponse;
import com.siwol025.flight_monitor.monitor.service.FlightSeatGradePriceService;
import com.siwol025.flight_monitor.monitor.service.PriceMonitorService;
import com.siwol025.flight_monitor.monitor.utils.DynamicTtlCalculator;
import com.siwol025.flight_monitor.subscription.domain.flight.Flight;
import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import com.siwol025.flight_monitor.subscription.service.FlightFetcher;
import com.siwol025.flight_monitor.subscription.service.FlightService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class ApiCallOptimizationTest {

    @Autowired
    private PriceMonitorService priceMonitorService; // 사용자 설정에 따라 CoolDownMonitorService일 수 있음

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockitoBean private FlightFetcher flightFetcher;
    @MockitoBean private FlightService flightService;
    @MockitoBean private FlightSeatGradePriceService flightSeatGradePriceService;
    @MockitoBean private DynamicTtlCalculator ttlCalculator;

    private static final int FLIGHT_COUNT = 100;
    private static final int SCHEDULE_CYCLES = 10;
    private static final int TOTAL_ATTEMPTS = FLIGHT_COUNT * SCHEDULE_CYCLES;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();

        // 100개의 항공권에 대한 외부 API 응답 및 엔티티 Mocking
        given(flightFetcher.fetchMockFlight(anyLong())).willAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            MockFlightResponse resp = mock(MockFlightResponse.class);
            given(resp.id()).willReturn(id);
            given(resp.getPriceBySeatClass(any())).willReturn(new BigDecimal("50000"));
            return resp;
        });

        given(flightService.getFlight(anyLong())).willAnswer(invocation -> {
            Flight mockFlight = mock(Flight.class);
            // 모든 항공권의 출발일을 10일 뒤로 설정하여 TTL이 넉넉하게 잡히도록 유도
            given(mockFlight.getDepartureTime()).willReturn(LocalDateTime.now().plusDays(10));
            return mockFlight;
        });

        // 쿨다운 TTL을 6시간으로 강제 고정 (테스트 중 만료되지 않게 함)
        given(ttlCalculator.calculateCooldownTtl(any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(Duration.ofHours(6));
    }

    @AfterEach
    void tearDown() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    @DisplayName("Redis TTL을 적용하면 스케줄러가 여러 번 실행되어도 외부 API 호출이 1회로 제한된다.")
    void externalApiCallReductionTest() {
        // given: 스케줄러가 10번(SCHEDULE_CYCLES) 실행되면서 100개(FLIGHT_COUNT)의 항공권을 조회하는 상황 모사
        for (int cycle = 0; cycle < SCHEDULE_CYCLES; cycle++) {
            for (long flightId = 1; flightId <= FLIGHT_COUNT; flightId++) {
                // when: 모니터링 로직 1000번 수행
                priceMonitorService.checkAndUpdatePrice(flightId, SeatGrade.ECONOMY);
            }
        }

        // then: 외부 API(flightFetcher) 호출 횟수 검증
        long actualApiCallCount = mockingDetails(flightFetcher).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("fetchMockFlight"))
                .count();

        System.out.println("🚨 모니터링 시도 횟수: " + TOTAL_ATTEMPTS + "회");
        System.out.println("🚨 실제 외부 API 호출 횟수: " + actualApiCallCount + "회");

        // 팩트 검증: 총 1000번의 로직 실행 중, 외부 API는 항공권 개수(100)만큼만 정확히 호출되어야 함
        assertThat(actualApiCallCount).isEqualTo(FLIGHT_COUNT);
    }
}