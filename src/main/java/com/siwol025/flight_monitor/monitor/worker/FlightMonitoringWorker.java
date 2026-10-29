package com.siwol025.flight_monitor.monitor.worker;

import com.siwol025.flight_monitor.global.exception.custom.AcquireInterruptedException;
import com.siwol025.flight_monitor.monitor.utils.TaskQueueConsumerManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

@Slf4j
@Component
@Profile("!mock")
public class FlightMonitoringWorker {

    // 폴러 스레드 이름 접두사 — 스레드 덤프/로그에서 컨슈머 폴러를 식별하기 위한 규약.
    private static final String POLLER_THREAD_NAME_PREFIX = "MonitorPoller-";
    // 오류 뒤 다시 돌기 전 쉬는 시간(운영 조정 대상 아님). Redis 장애처럼 실패가 이어져도 폴러 하나당 초당 1회만 시도한다.
    private static final Duration ERROR_BACKOFF = Duration.ofSeconds(1);

    private final TaskQueueConsumerManager queueManager;
    private final MonitoringTaskProcessor taskProcessor;
    private final InFlightBackpressure backpressure;
    private final Executor executor;
    private final int workerCount;
    private final long blockPopTimeoutSeconds;

    private volatile boolean isRunning = true;
    private final List<Thread> pollerThreads = new ArrayList<>();

    public FlightMonitoringWorker(TaskQueueConsumerManager queueManager, MonitoringTaskProcessor taskProcessor,
                                  InFlightBackpressure backpressure,
                                  @Qualifier("monitoringTaskExecutor") Executor executor,
                                  @Value("${monitor.worker.count:3}") int workerCount,
                                  @Value("${monitor.consumer.block-pop-timeout-seconds:3}") long blockPopTimeoutSeconds) {
        this.queueManager = queueManager;
        this.taskProcessor = taskProcessor;
        this.backpressure = backpressure;
        this.executor = executor;
        this.workerCount = workerCount;
        this.blockPopTimeoutSeconds = blockPopTimeoutSeconds;
    }

    @PostConstruct
    public void startWorkers() {
        for (int i = 0; i < workerCount; i++) {
            Thread pollerThread = new Thread(this::pollQueue, POLLER_THREAD_NAME_PREFIX + i);
            pollerThread.setDaemon(true);
            pollerThread.start();
            pollerThreads.add(pollerThread);
        }
    }

    private void pollQueue() {
        while (isRunning) {
            try {
                backpressure.acquire();
            } catch (AcquireInterruptedException e) {
                // acquire 대기 중 인터럽트 → permit 미보유 상태 → release 금지, 폴링 종료.
                log.info("[FlightMonitoringWorker] acquire 대기 중 중단되었습니다. 폴링을 종료합니다.");
                break;
            } catch (RuntimeException e) {
                // rate 단계 실패(Redis 오류 등). acquire가 permit을 이미 반납했으므로 release 금지. 폴러는 죽지 않고 쉬었다 다시 돈다.
                log.error("[FlightMonitoringWorker] permit·rate 토큰 획득 중 오류 발생", e);
                if (!backOff()) {
                    break;
                }
                continue;
            }

            try {
                String payload = queueManager.blockAndPopTask(blockPopTimeoutSeconds);

                if (payload == null && queueManager.hasPendingFallbackTask()) {
                    payload = queueManager.pollFallbackQueue();
                }

                if (payload == null) {
                    backpressure.release();
                    continue;
                }
                String taskPayload = payload;
                executor.execute(() -> taskProcessor.processTask(taskPayload));

            } catch (Exception e) {
                // acquire()가 선행 성공한 뒤에만 이 지점에 도달하므로 permit은 반드시 보유 상태 → 예외 경로에서 무조건 반납(누수 방지).
                backpressure.release();
                if (Thread.currentThread().isInterrupted()) {
                    log.info("[FlightMonitoringWorker] 모니터링 폴러 스레드가 중단되었습니다. 폴링을 종료합니다.");
                    break;
                }
                log.error("[FlightMonitoringWorker] 폴링 또는 작업 위임 중 오류 발생", e);
                if (!backOff()) {
                    break;
                }
            }
        }
    }

    /**
     * 실패가 이어질 때 빈 루프로 CPU·로그가 폭주하지 않게 잠깐 쉰다. 쉬는 중 인터럽트(종료 신호)면 false.
     */
    private boolean backOff() {
        try {
            Thread.sleep(ERROR_BACKOFF);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("[FlightMonitoringWorker] 오류 후 대기 중 중단되었습니다. 폴링을 종료합니다.");
            return false;
        }
    }

    @PreDestroy
    public void stopWorkers() {
        isRunning = false;
        for (Thread pollerThread : pollerThreads) {
            if (pollerThread != null) {
                pollerThread.interrupt();
            }
        }
        log.info("[FlightMonitoringWorker] 워커가 정상적으로 종료됩니다.");
    }
}
