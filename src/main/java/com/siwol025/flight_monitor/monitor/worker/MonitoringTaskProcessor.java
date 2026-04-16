package com.siwol025.flight_monitor.monitor.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siwol025.flight_monitor.monitor.service.PriceMonitorService;
import com.siwol025.flight_monitor.subscription.dto.FlightMonitorTaskDto;
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

    public MonitoringTaskProcessor(ObjectMapper objectMapper, PriceMonitorService priceMonitorService) {
        this.objectMapper = objectMapper;
        this.priceMonitorService = priceMonitorService;
    }

    @Async("monitoringTaskExecutor")
    public void processTask(String jsonPayload) {
        try {
            FlightMonitorTaskDto taskDto = objectMapper.readValue(jsonPayload, FlightMonitorTaskDto.class);
            priceMonitorService.checkPriceAndNotify(taskDto);
        } catch (Exception e) {
            log.error("🚨 [Monitoring Worker] 작업 처리 실패: {}", e.getMessage());
        }
    }
}
