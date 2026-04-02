package com.siwol025.flight_monitor.monitor.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siwol025.flight_monitor.monitor.service.PriceMonitorService;
import com.siwol025.flight_monitor.monitor.utils.TaskQueueConsumerManager;
import com.siwol025.flight_monitor.subscription.dto.FlightMonitorTaskDto;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FlightMonitoringWorker {

    private final ObjectMapper objectMapper;
    private final PriceMonitorService priceMonitorService;
    private final TaskQueueConsumerManager taskQueueConsumerManager;
    private final Executor taskExecutor;

    private volatile boolean isRunning = true;
    private Thread pollerThread;

    public FlightMonitoringWorker(
            TaskQueueConsumerManager taskQueueConsumerManager,
            PriceMonitorService priceMonitorService,
            ObjectMapper objectMapper,
            @Qualifier("monitoringTaskExecutor") Executor taskExecutor) {
        this.taskQueueConsumerManager = taskQueueConsumerManager;
        this.priceMonitorService = priceMonitorService;
        this.objectMapper = objectMapper;
        this.taskExecutor = taskExecutor;
    }

    @PostConstruct
    public void startWorkers() {
        pollerThread = new Thread(this::pollQueue, "MonitorPoller");
        pollerThread.setDaemon(true);
        pollerThread.start();
        log.info("FlightMonitoring Poller started.");
    }

    private void pollQueue() {
        while (isRunning) {
            try {
                String jsonPayload = taskQueueConsumerManager.popTask();

                if (jsonPayload != null) {
                    taskExecutor.execute(() -> processTask(jsonPayload));
                }
            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) {
                    log.info("MonitorPoller thread is interrupted. Stopping polling...");
                    break;
                }
                log.error("Error occurred while polling or delegating task", e);
            }
        }
    }

    private void processTask(String jsonPayload) {
        try {
            FlightMonitorTaskDto taskDto = objectMapper.readValue(jsonPayload, FlightMonitorTaskDto.class);
            log.info("[Monitoring Worker] 작업 처리 시작: FlightID={}, SeatGrade={}", taskDto.flightId(), taskDto.seatGrade());

            priceMonitorService.checkPriceAndNotify(taskDto);
        } catch (Exception e) {
            log.error("🚨 [Monitoring Worker] 작업 처리 실패 (Payload: {}): {}", jsonPayload, e.getMessage());
        }
    }

    @PreDestroy
    public void stopWorkers() {
        isRunning = false;
        if (pollerThread != null) {
            pollerThread.interrupt();
        }
        log.info("FlightMonitoringWorker is shutting down gracefully...");
    }
}
