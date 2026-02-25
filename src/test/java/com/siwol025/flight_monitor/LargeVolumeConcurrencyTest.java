package com.siwol025.flight_monitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

import lombok.extern.slf4j.Slf4j;
import com.siwol025.flight_monitor.mock.flight.dto.response.MockFlightResponse;
import com.siwol025.flight_monitor.monitor.domain.FlightSeatGradePrice;
import com.siwol025.flight_monitor.monitor.producer.NotificationProducer;
import com.siwol025.flight_monitor.monitor.repository.NotificationHistoryRepository;
import com.siwol025.flight_monitor.monitor.service.FlightSeatGradePriceService;
import com.siwol025.flight_monitor.monitor.service.PriceMonitorService;
import com.siwol025.flight_monitor.subscription.domain.flight.Flight;
import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import com.siwol025.flight_monitor.subscription.service.FlightFetcher;
import com.siwol025.flight_monitor.subscription.service.FlightService;
import com.siwol025.flight_monitor.subscription.service.SubscriptionService;
import com.siwol025.flight_monitor.user.dto.UserEmailDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.redis.core.StringRedisTemplate;

@Slf4j
@SpringBootTest
public class LargeVolumeConcurrencyTest {

    @Autowired
    private PriceMonitorService priceMonitorService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private NotificationHistoryRepository historyRepository;

    // 의존성 Mocking
    @MockitoBean private SubscriptionService subscriptionService;
    @MockitoBean private FlightFetcher flightFetcher;
    @MockitoBean private FlightService flightService;
    @MockitoBean private FlightSeatGradePriceService flightSeatGradePriceService;

    // 워커 개입을 차단하기 위한 Mock 객체 추가
    @MockitoBean private NotificationProducer producer;

    private static final int TARGET_COUNT = 1000;

    @BeforeEach
    void setUp() {
        historyRepository.deleteAll();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb(); // 레디스 완전 초기화

        // 1. 구독자 강제 세팅 (1명)
        given(subscriptionService.getSubscribers(anyLong()))
                .willReturn(List.of(new UserEmailDto("test@example.com")));

        // 2. 외부 API 가격 조회 강제 세팅 (새로운 가격: 50,000원)
        given(flightFetcher.fetchMockFlight(anyLong())).willAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            MockFlightResponse resp = mock(MockFlightResponse.class);
            given(resp.id()).willReturn(id);
            given(resp.flightNumber()).willReturn("KE" + id);
            given(resp.getPriceBySeatClass(any())).willReturn(new BigDecimal("50000"));
            return resp;
        });

        // 3. 항공권 조회 강제 세팅
        Flight mockFlight = mock(Flight.class);
        given(flightService.getFlight(anyLong())).willReturn(mockFlight);

        // 4. DB에 저장된 기존 가격 강제 세팅 (기존 가격: 100,000원) -> 무조건 하락했다고 판단하게 만듦
        given(flightSeatGradePriceService.findOrCreateFlightSeatGradePrice(any(), any(), any()))
                .willAnswer(invocation -> {
                    FlightSeatGradePrice mockPrice = mock(FlightSeatGradePrice.class);
                    given(mockPrice.getPrice()).willReturn(new BigDecimal("100000"));
                    return mockPrice;
                });
    }

    @AfterEach
    void tearDown() {
        historyRepository.deleteAll();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    @DisplayName("분산 락 적용 시, 처리 지연으로 스케줄러가 중첩 실행되어도 이벤트가 중복 발행되지 않는다.")
    void largeVolumeSchedulerOverlapLockTest() throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);

        executorService.submit(() -> {
            try {
                processLargeVolume(1, TARGET_COUNT);
            } catch (Exception e) {
                log.error("🔴 스레드 1 실행 중 치명적 에러 발생", e);
            } finally {
                latch.countDown();
            }
        });

        Thread.sleep(100);

        executorService.submit(() -> {
            try {
                processLargeVolume(1, TARGET_COUNT);
            } catch (Exception e) {
                log.error("🔴 스레드 2 실행 중 치명적 에러 발생", e);
            } finally {
                latch.countDown();
            }
        });

        latch.await();

        // 불안정한 큐 사이즈 조회를 제거하고, producer 객체의 알림 발행 메서드가 목표 건수만큼 호출되었는지 확인합니다.
        long totalGeneratedEvents = mockingDetails(producer).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("publishPriceDrop"))
                .count();

        log.info("🚨 [동시성 테스트 결과] 목표 건수: {} / 실제 발생 건수: {}", TARGET_COUNT, totalGeneratedEvents);

        assertThat(totalGeneratedEvents).isEqualTo(TARGET_COUNT);
    }

    private void processLargeVolume(int startId, int endId) {
        IntStream.rangeClosed(startId, endId).forEach(id -> {
            priceMonitorService.checkAndUpdatePrice((long) id, SeatGrade.ECONOMY);
        });
    }
}