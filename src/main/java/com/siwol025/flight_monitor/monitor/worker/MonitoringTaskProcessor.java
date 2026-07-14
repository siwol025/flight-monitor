package com.siwol025.flight_monitor.monitor.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siwol025.flight_monitor.monitor.metrics.PipelineMetrics;
import com.siwol025.flight_monitor.monitor.service.PriceMonitorService;
import com.siwol025.flight_monitor.subscription.dto.FlightMonitorTaskDto;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!mock")
public class MonitoringTaskProcessor {

    private final ObjectMapper objectMapper;
    private final PriceMonitorService priceMonitorService;
    private final PipelineMetrics pipelineMetrics;

    public MonitoringTaskProcessor(ObjectMapper objectMapper, PriceMonitorService priceMonitorService,
                                    PipelineMetrics pipelineMetrics) {
        this.objectMapper = objectMapper;
        this.priceMonitorService = priceMonitorService;
        this.pipelineMetrics = pipelineMetrics;
    }

    @Async("monitoringTaskExecutor")
    public void processTask(String jsonPayload) {
        long startNanos = System.nanoTime();
        pipelineMetrics.incrementInFlight();
        try {
            FlightMonitorTaskDto taskDto = objectMapper.readValue(jsonPayload, FlightMonitorTaskDto.class);
            priceMonitorService.checkPriceAndNotify(taskDto);
            pipelineMetrics.recordTaskProcessed();
        } catch (Exception e) {
            log.error("[MonitoringTaskProcessor] 작업 처리 실패", e);
        } finally {
            pipelineMetrics.decrementInFlight();
            pipelineMetrics.recordTaskLatency(Duration.ofNanos(System.nanoTime() - startNanos));
        }
    }
}
