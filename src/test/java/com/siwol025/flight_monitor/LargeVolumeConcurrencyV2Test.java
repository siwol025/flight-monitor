package com.siwol025.flight_monitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.siwol025.flight_monitor.mock.flight.dto.response.MockFlightResponse;
import com.siwol025.flight_monitor.monitor.domain.FlightSeatGradePrice;
import com.siwol025.flight_monitor.monitor.repository.FlightSeatGradePriceRepository;
import com.siwol025.flight_monitor.monitor.repository.NotificationHistoryRepository;
import com.siwol025.flight_monitor.monitor.scheduler.PriceScheduler;
import com.siwol025.flight_monitor.monitor.service.PriceMonitorService;
import com.siwol025.flight_monitor.subscription.domain.Subscription;
import com.siwol025.flight_monitor.subscription.domain.flight.Flight;
import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import com.siwol025.flight_monitor.subscription.repository.FlightRepository;
import com.siwol025.flight_monitor.subscription.repository.SubscriptionRepository;
import com.siwol025.flight_monitor.subscription.service.FlightFetcher;
import com.siwol025.flight_monitor.user.domain.User;
import com.siwol025.flight_monitor.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("dev")
public class LargeVolumeConcurrencyV2Test {

    @Autowired private PriceMonitorService priceMonitorService;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private NotificationHistoryRepository historyRepository;

    @Autowired private FlightRepository flightRepository;
    @Autowired private FlightSeatGradePriceRepository priceRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private UserRepository userRepository; // User 저장을 위해 추가

    @MockitoBean private FlightFetcher flightFetcher; // 외부 API 호출만 Mocking 유지
    @MockitoBean private PriceScheduler priceScheduler;

    private static final String QUEUE_NAME = "notification:queue";
    private static final String TASK_QUEUE = "email:task:queue";
    private static final int TARGET_COUNT = 1000;

    @BeforeEach
    void setUp() {
        // 1. 기존 데이터 초기화 (외래키 제약조건 위배를 막기 위해 자식 테이블부터 역순 삭제)
        historyRepository.deleteAllInBatch();
        priceRepository.deleteAllInBatch();
        subscriptionRepository.deleteAllInBatch();
        flightRepository.deleteAllInBatch();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();

        // 2. 단일 테스트 유저 생성 및 저장
        // 주의: User 엔티티의 실제 필수 값(Not Null) 구조에 맞춰 수정하십시오.
        List<User> byId = userRepository.findAll();
        User testUser = byId.get(0);

        userRepository.save(testUser);

        // 3. Flight 1000건 생성
        List<Flight> flights = new ArrayList<>();
        LocalDateTime departureTime = LocalDateTime.now().plusDays(1);
        LocalDateTime arrivalTime = departureTime.plusHours(2);

        for (long i = 1; i <= TARGET_COUNT; i++) {
            Flight flight = Flight.builder()
                    .flightId(i)
                    .flightNumber("KE" + String.format("%04d", i))
                    .airlineCode("KE")
                    .departureAirport("ICN")
                    .arrivalAirport("NRT")
                    .departureTime(departureTime)
                    .arrivalTime(arrivalTime)
                    .build();
            flights.add(flight);
        }
        List<Flight> savedFlights = flightRepository.saveAll(flights);

        // 4. 가격(FlightSeatGradePrice) 및 구독(Subscription) 정보 1000건씩 생성
        List<FlightSeatGradePrice> prices = new ArrayList<>();
        List<Subscription> subscriptions = new ArrayList<>();
        BigDecimal initialPrice = new BigDecimal("100000"); // 초기 가격 10만 원 설정

        for (Flight savedFlight : savedFlights) {
            prices.add(FlightSeatGradePrice.builder()
                    .flight(savedFlight)
                    .seatGrade(SeatGrade.ECONOMY)
                    .price(initialPrice)
                    .build());

            subscriptions.add(Subscription.builder()
                    .user(testUser)
                    .flight(savedFlight)
                    .seatGrade(SeatGrade.ECONOMY)
                    .price(initialPrice)
                    .build());
        }
        priceRepository.saveAll(prices);
        subscriptionRepository.saveAll(subscriptions);

        // 5. 외부 API 최신 가격 50,000원으로 조작 (무조건 하락 판정)
        given(flightFetcher.fetchMockFlight(any())).willAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            MockFlightResponse resp = mock(MockFlightResponse.class);
            given(resp.id()).willReturn(id);
            given(resp.flightNumber()).willReturn("KE" + String.format("%04d", id));
            given(resp.getPriceBySeatClass(any())).willReturn(new BigDecimal("50000"));
            return resp;
        });
    }

    @AfterEach
    void tearDown() {
        //historyRepository.deleteAllInBatch();
        priceRepository.deleteAllInBatch();
        subscriptionRepository.deleteAllInBatch();
        flightRepository.deleteAllInBatch();
        //userRepository.deleteAllInBatch();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    @DisplayName("스케줄러 A가 끝나기 전에 B가 시작되어 동일한 항공권을 중복 검사하는 상황 재현")
    void visualizeSchedulerOverlap() throws Exception {
        // given: 항공권 10건만 대상으로 테스트 (눈으로 보기 편하게)
        int targetCount = 10;

        // 1. 데이터 세팅 (이전과 동일하게 10건 세팅)
        // setupFlightData(targetCount);

        // 2. 두 개의 스레드 준비 (스케줄러 A, 스케줄러 B 역할)
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        // when: 스케줄러 A 시작
        executorService.submit(() -> {
            System.out.println("🚩 [스케줄러 A] 모니터링 시작");
            IntStream.rangeClosed(1, targetCount).forEach(id -> {
                try {
                    // A는 한 건 처리할 때마다 500ms씩 걸린다고 가정
                    Thread.sleep(500);
                    priceMonitorService.checkAndUpdatePrice((long) id, SeatGrade.ECONOMY);
                    System.out.println("  -> [스케줄러 A] 항공권 " + id + " 검사 완료");
                } catch (Exception e) { e.printStackTrace(); }
            });
            System.out.println("🏁 [스케줄러 A] 전체 종료");
        });

        // 3. 1.5초 뒤에 스케줄러 B가 시작 (A가 아직 3번쯤 검사하고 있을 때)
        Thread.sleep(1500);

        executorService.submit(() -> {
            System.out.println("🔥 [스케줄러 B] (새로운 주기) 모니터링 시작!");
            IntStream.rangeClosed(1, targetCount).forEach(id -> {
                try {
                    Thread.sleep(500);
                    priceMonitorService.checkAndUpdatePrice((long) id, SeatGrade.ECONOMY);
                    System.out.println("  -> [스케줄러 B] 항공권 " + id + " 검사 완료 (중복 발생 지점!)");
                } catch (Exception e) { e.printStackTrace(); }
            });
            System.out.println("🏁 [스케줄러 B] 전체 종료");
        });

        // then: 모든 처리가 끝날 때까지 대기 (넉넉히 10초)
        Thread.sleep(10000);
    }

    @Test
    @DisplayName("대량 데이터 처리 지연으로 스케줄러가 중복 실행될 경우 이벤트가 중복 발행된다.")
    void largeVolumeSchedulerOverlapTest() throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);

        // 1회차 스케줄러 스레드
        executorService.submit(() -> {
            try {
                processLargeVolume(1, TARGET_COUNT);
            } finally {
                latch.countDown();
            }
        });

        // 처리 지연 발생 모사 (0.1초)
        Thread.sleep(100);

        // 2회차 스케줄러 스레드 (오버랩)
        executorService.submit(() -> {
            try {
                processLargeVolume(1, TARGET_COUNT);
            } finally {
                latch.countDown();
            }
        });

        latch.await();

        // 워커가 큐를 소모하여 DB에 이력을 기록할 충분한 시간 부여
        Thread.sleep(15000);

        Long queueSize = redisTemplate.opsForList().size(QUEUE_NAME);
        Long taskQueueSize = redisTemplate.opsForList().size(TASK_QUEUE);
        long historyCount = historyRepository.count();

        long totalGeneratedEvents = (queueSize != null ? queueSize : 0)
                + (taskQueueSize != null ? taskQueueSize : 0)
                + historyCount;

        System.out.println("🚨 [동시성 테스트 결과] 목표 건수: " + TARGET_COUNT + " / 실제 발생 건수(중복 포함): " + totalGeneratedEvents);

        // 검증: 동시성 제어 부재로 인해 1000건을 초과하는 알림(중복)이 발생함을 증명
        assertThat(totalGeneratedEvents).isGreaterThan(TARGET_COUNT);
    }

    private void processLargeVolume(int startId, int endId) {
        // DB에 삽입된 flightId (1 ~ 1000)를 대상으로 실행
        IntStream.rangeClosed(startId, endId).forEach(id -> {
            priceMonitorService.checkAndUpdatePrice((long) id, SeatGrade.ECONOMY);
        });
    }
}
