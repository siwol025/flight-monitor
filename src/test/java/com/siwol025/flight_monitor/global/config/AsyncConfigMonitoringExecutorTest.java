package com.siwol025.flight_monitor.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.siwol025.flight_monitor.monitor.metrics.PipelineMetrics;
import com.siwol025.flight_monitor.fare.LowestFareFinder;
import com.siwol025.flight_monitor.monitor.service.PriceMonitorService;
import com.siwol025.flight_monitor.monitor.service.RouteLatestFareService;
import com.siwol025.flight_monitor.monitor.utils.TaskQueueConsumerManager;
import com.siwol025.flight_monitor.monitor.worker.FlightMonitoringWorker;
import com.siwol025.flight_monitor.monitor.worker.InFlightBackpressure;
import com.siwol025.flight_monitor.monitor.worker.MonitoringTaskProcessor;
import com.siwol025.flight_monitor.monitor.worker.RedisRateLimiter;
import com.siwol025.flight_monitor.notification.producer.NotificationPublisher;
import com.siwol025.flight_monitor.route.domain.Route;
import com.siwol025.flight_monitor.route.domain.RouteKey;
import com.siwol025.flight_monitor.route.domain.SeatGrade;
import com.siwol025.flight_monitor.route.repository.RouteRepository;
import com.siwol025.flight_monitor.route.service.RouteService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 가드: monitoringTaskExecutor에 제출된 태스크가 작업마다 가상 스레드(MonitorWorker-)에서 실행되는지 검증한다.
 * 가상 스레드 executor에는 상한이 없으므로 동시 처리 수 상한은 InFlightBackpressure 세마포어가 단독으로 결정한다.
 */
@SpringBootTest(classes = {AsyncConfig.class})
class AsyncConfigMonitoringExecutorTest {

    @MockitoBean
    private PipelineMetrics pipelineMetrics;

    @Autowired
    @Qualifier("monitoringTaskExecutor")
    private Executor monitoringTaskExecutor;

    @Test
    void 제출된태스크_가상스레드에서_실행됨() throws Exception {
        CompletableFuture<Boolean> virtualFuture = new CompletableFuture<>();
        CompletableFuture<String> nameFuture = new CompletableFuture<>();

        monitoringTaskExecutor.execute(() -> {
            virtualFuture.complete(Thread.currentThread().isVirtual());
            nameFuture.complete(Thread.currentThread().getName());
        });

        assertThat(virtualFuture.get(5, TimeUnit.SECONDS))
                .as("monitoringTaskExecutor 태스크는 가상 스레드에서 실행되어야 한다")
                .isTrue();
        assertThat(nameFuture.get(5, TimeUnit.SECONDS))
                .as("monitoringTaskExecutor 스레드 이름은 MonitorWorker- 접두어를 가져야 한다")
                .startsWith("MonitorWorker-");
    }

    @Test
    void 동시제출_동시실행수는_maxInFlight를_넘지않음() {
        // 폴러 수 < maxInFlight: 폴러 2개로 4건이 동시에 돌아야 작업이 executor에서 비동기로 실행된 것이다
        int maxInFlight = 4;
        int pollerCount = 2;
        int totalTasks = 40;
        AtomicInteger supplied = new AtomicInteger();
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maxRunning = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();

        // HTTP 경계(테스트 전용 가짜 운임 포트): 외부 API 응답 지연을 흉내 내며 동시 호출 수를 잰다.
        // 운임 없음(empty)이면 PriceMonitorService가 저장 없이 바로 끝난다
        LowestFareFinder slowApi = routeKey -> {
            maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                running.decrementAndGet();
                completed.incrementAndGet();
            }
            return Optional.empty();
        };
        // DB 경계: 작업의 노선 ID로 감시할 노선을 찾는다
        RouteRepository routeRepository = mock(RouteRepository.class);
        given(routeRepository.findById(1L)).willReturn(Optional.of(
                new Route(new RouteKey("ICN", "NRT", LocalDate.of(2026, 11, 26), SeatGrade.ECONOMY))));
        // Redis 큐 경계: 작업을 totalTasks건 공급한 뒤에는 실제 BRPOP처럼 잠시 블로킹하다 타임아웃(null)한다.
        // 공급이 유한해야 상한이 깨지는 회귀에서 테스트가 멈추지 않고 바로 실패한다
        TaskQueueConsumerManager queue = mock(TaskQueueConsumerManager.class);
        given(queue.blockAndPopTask(anyLong())).willAnswer(invocation -> {
            if (supplied.getAndIncrement() < totalTasks) {
                return "1";
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        });

        InFlightBackpressure backpressure = new InFlightBackpressure(maxInFlight);
        PipelineMetrics metrics = new PipelineMetrics(new SimpleMeterRegistry());
        PriceMonitorService priceMonitorService = new PriceMonitorService(routeRepository, mock(RouteService.class),
                slowApi, mock(RouteLatestFareService.class), mock(NotificationPublisher.class), metrics,
                Clock.systemUTC(), new StaticListableBeanFactory().getBeanProvider(RedisRateLimiter.class));
        MonitoringTaskProcessor processor = new MonitoringTaskProcessor(priceMonitorService, metrics, backpressure);
        FlightMonitoringWorker worker = new FlightMonitoringWorker(queue, processor, backpressure,
                monitoringTaskExecutor, pollerCount, 3);

        worker.startWorkers();
        try {
            await().atMost(Duration.ofSeconds(10)).until(() -> completed.get() == totalTasks);
        } finally {
            worker.stopWorkers();
        }

        assertThat(maxRunning.get())
                .as("동시 실행 수 상한은 세마포어(maxInFlight=%d)가 만든다", maxInFlight)
                .isLessThanOrEqualTo(maxInFlight);
        assertThat(maxRunning.get())
                .as("폴러 %d개로 %d건이 동시에 돌아야 작업이 executor에서 비동기로 실행된 것이다", pollerCount, maxInFlight)
                .isEqualTo(maxInFlight);
    }
}
