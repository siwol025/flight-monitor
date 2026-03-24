package com.siwol025.flight_monitor.monitor.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siwol025.flight_monitor.monitor.service.PriceMonitorService;
import com.siwol025.flight_monitor.monitor.utils.TaskQueueConsumerManager;
import com.siwol025.flight_monitor.subscription.dto.FlightMonitorTaskDto;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FlightMonitoringWorker {

    private final ObjectMapper objectMapper;
    private final PriceMonitorService priceMonitorService;
    private final TaskQueueConsumerManager taskQueueConsumerManager;

    private static final String TASK_QUEUE_KEY = "monitoring:task:queue";

    @Value("${monitor.worker.count:3}")
    private int workerCount;

    private ExecutorService executorService;
    private volatile boolean isRunning = true;

    @EventListener(ApplicationReadyEvent.class)
    public void startWorkers() {
        executorService = Executors.newFixedThreadPool(workerCount);
        for (int i = 0; i < workerCount; i++) {
            executorService.submit(this::pollQueue);
        }
        log.info("✅ [Consumer] {}개의 모니터링 워커 스레드가 시작되었습니다.", workerCount);
    }

    private void pollQueue() {
        while (isRunning && !Thread.currentThread().isInterrupted()) {
            try {
                String jsonPayload = taskQueueConsumerManager.popTask();

                if (jsonPayload == null) {
                    Thread.sleep(1000);
                    continue;
                }

                processTask(jsonPayload);
            } catch (Exception e) {
                log.error("🚨 [Consumer] 큐 폴링 중 오류 발생: {}", e.getMessage());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private void processTask(String jsonPayload) {
        try {
            FlightMonitorTaskDto taskDto = objectMapper.readValue(jsonPayload, FlightMonitorTaskDto.class);
            log.info("📥 [Consumer] 작업 처리 시작: FlightID={}, SeatGrade={}", taskDto.flightId(), taskDto.seatGrade());

            priceMonitorService.checkPriceAndNotify(taskDto);
        } catch (Exception e) {
            log.error("🚨 [Consumer] 작업 처리 실패 (Payload: {}): {}", jsonPayload, e.getMessage());
        }
    }

    @PreDestroy
    public void stopWorkers() {
        isRunning = false;
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }
}
