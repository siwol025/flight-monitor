package com.siwol025.flight_monitor.monitor.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.siwol025.flight_monitor.global.annotation.DistributedLock;
import com.siwol025.flight_monitor.subscription.dto.FlightMonitorTaskDto;
import com.siwol025.flight_monitor.subscription.service.SubscriptionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FlightMonitoringProducer {

    private final SubscriptionService subscriptionService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String TASK_QUEUE_KEY = "monitoring:task:queue";
    private static final int MAX_QUEUE_THRESHOLD = 10000;

    @Scheduled(fixedRate = 60000)
    @DistributedLock(key = "'flight:monitoring:producer:lock'", waitTime = 5, leaseTime = 30)
    public void produceMonitoringTasks() {
        Long currentQueueSize = redisTemplate.opsForList().size(TASK_QUEUE_KEY);

        if (currentQueueSize != null && currentQueueSize > MAX_QUEUE_THRESHOLD) {
            log.warn("⏳ [Skip] 큐 대기 작업 임계치 초과 (현재: {}건). 발행 중단", currentQueueSize);
            return;
        }

        List<FlightMonitorTaskDto> targets = subscriptionService.getActiveFlights();
        if (targets.isEmpty()) {
            log.info("✅ [Producer] 활성 모니터링 대상 없음");
            return;
        }

        List<String> jsonPayloads = targets.stream()
                .map(this::toJson)
                .toList();

        redisTemplate.opsForList().leftPushAll(TASK_QUEUE_KEY, jsonPayloads);

        log.info("=== [가격 모니터링 스케줄러 시작] 총 감시 대상 수: {}건 ===", targets.size());
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
