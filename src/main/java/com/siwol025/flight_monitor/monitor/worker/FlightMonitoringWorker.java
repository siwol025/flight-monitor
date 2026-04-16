package com.siwol025.flight_monitor.monitor.worker;

import com.siwol025.flight_monitor.monitor.utils.TaskQueueConsumerManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!mock")
public class FlightMonitoringWorker {

    private final TaskQueueConsumerManager taskQueueConsumerManager;
    private final MonitoringTaskProcessor taskProcessor;

    private volatile boolean isRunning = true;
    private Thread pollerThread;

    @PostConstruct
    public void startWorkers() {
        pollerThread = new Thread(this::pollQueue, "MonitorPoller");
        pollerThread.setDaemon(true);
        pollerThread.start();
    }

    private void pollQueue() {
        while (isRunning) {
            try {
                String firstPayload = taskQueueConsumerManager.blockAndPopTask(1);

                if (firstPayload != null) {
                    taskProcessor.processTask(firstPayload);

                    List<String> batchPayloads = taskQueueConsumerManager.popTasksBatch(99);

                    if (batchPayloads != null && !batchPayloads.isEmpty()) {
                        for (String payload : batchPayloads) {
                            taskProcessor.processTask(payload);
                        }
                    }
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

    @PreDestroy
    public void stopWorkers() {
        isRunning = false;
        if (pollerThread != null) {
            pollerThread.interrupt();
        }
        log.info("FlightMonitoringWorker is shutting down gracefully...");
    }
}
