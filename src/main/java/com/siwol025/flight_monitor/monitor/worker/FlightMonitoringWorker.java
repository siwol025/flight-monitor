package com.siwol025.flight_monitor.monitor.worker;

import com.google.common.util.concurrent.RateLimiter;
import com.siwol025.flight_monitor.monitor.utils.TaskQueueConsumerManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!mock")
public class FlightMonitoringWorker {

    private final TaskQueueConsumerManager queueManager;
    private final MonitoringTaskProcessor taskProcessor;

    @Value("${monitor.rate-limiter.permits-per-second:90.0}")
    private double permitsPerSecond = 90.0;

    private RateLimiter rateLimiter;

    private volatile boolean isRunning = true;
    private Thread pollerThread;

    @PostConstruct
    public void startWorkers() {
        rateLimiter = RateLimiter.create(permitsPerSecond);
        pollerThread = new Thread(this::pollQueue, "MonitorPoller");
        pollerThread.setDaemon(true);
        pollerThread.start();
    }

    private void pollQueue() {
        while (isRunning) {
            try {
                rateLimiter.acquire();

                String payload = queueManager.blockAndPopTask(3);

                if (payload == null && queueManager.hasPendingFallbackTask()) {
                    payload = queueManager.pollFallbackQueue();
                }

                if (payload == null) {
                    continue;
                }
                taskProcessor.processTask(payload);

            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) {
                    log.info("[FlightMonitoringWorker] 모니터링 폴러 스레드가 중단되었습니다. 폴링을 종료합니다.");
                    break;
                }
                log.error("[FlightMonitoringWorker] 폴링 또는 작업 위임 중 오류 발생", e);
            }
        }
    }

    @PreDestroy
    public void stopWorkers() {
        isRunning = false;
        if (pollerThread != null) {
            pollerThread.interrupt();
        }
        log.info("[FlightMonitoringWorker] 워커가 정상적으로 종료됩니다.");
    }
}
