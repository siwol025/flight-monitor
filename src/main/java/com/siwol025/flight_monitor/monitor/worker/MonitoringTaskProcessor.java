package com.siwol025.flight_monitor.monitor.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siwol025.flight_monitor.monitor.metrics.PipelineMetrics;
import com.siwol025.flight_monitor.monitor.service.PriceMonitorService;
import com.siwol025.flight_monitor.subscription.dto.FlightMonitorTaskDto;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!mock")
public class MonitoringTaskProcessor {

    private final ObjectMapper objectMapper;
    private final PriceMonitorService priceMonitorService;
    private final PipelineMetrics pipelineMetrics;
    private final InFlightBackpressure backpressure;

    public MonitoringTaskProcessor(ObjectMapper objectMapper, PriceMonitorService priceMonitorService,
                                    PipelineMetrics pipelineMetrics, InFlightBackpressure backpressure) {
        this.objectMapper = objectMapper;
        this.priceMonitorService = priceMonitorService;
        this.pipelineMetrics = pipelineMetrics;
        this.backpressure = backpressure;
    }

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
            // 워커(폴러 스레드)가 디스패치 전 획득한 permit을 비동기 처리 완료 시점에 반납한다(스레드 경계를 넘는 acquire/release 대칭).
            backpressure.release();
        }
    }
}
