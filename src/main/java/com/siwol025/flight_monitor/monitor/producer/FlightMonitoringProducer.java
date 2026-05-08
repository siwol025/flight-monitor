package com.siwol025.flight_monitor.monitor.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.siwol025.flight_monitor.monitor.utils.TaskQueueManager;
import com.siwol025.flight_monitor.subscription.dto.FlightMonitorTaskDto;
import com.siwol025.flight_monitor.subscription.service.SubscriptionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!mock")
public class FlightMonitoringProducer {

    private final SubscriptionService subscriptionService;
    private final ObjectMapper objectMapper;
    private final TaskQueueManager taskQueueManager;

    private static final int MAX_QUEUE_THRESHOLD = 20000;
    private static final int CHUNK_SIZE = 1000;

    @Scheduled(fixedRate = 300000)
    public void produceMonitoringTasks() {
        Long currentQueueSize = taskQueueManager.getQueueSizeSafely();

        if (currentQueueSize != null && currentQueueSize > MAX_QUEUE_THRESHOLD) {
            log.warn("⏳ [Skip] 큐 대기 작업 임계치 초과 (현재: {}건). 발행 중단", currentQueueSize);
            return;
        }

        List<FlightMonitorTaskDto> targets = subscriptionService.getActiveFlights();
        if (targets.isEmpty()) {
            return;
        }

        for (int i = 0; i < targets.size(); i += CHUNK_SIZE) {
            int end = Math.min(i+CHUNK_SIZE, targets.size());
            List<FlightMonitorTaskDto> chunk = targets.subList(i, end);

            List<String> jsonPayloads = chunk.stream()
                    .map(this::toJson)
                    .toList();

            taskQueueManager.publishTasks(jsonPayloads);
        }
    }

    private String toJson(FlightMonitorTaskDto dto) {
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            log.error("🚨 [Producer] JSON 직렬화 실패: {}", dto, e);
            throw new RuntimeException("작업 페이로드 직렬화 오류", e);
        }
    }
}
