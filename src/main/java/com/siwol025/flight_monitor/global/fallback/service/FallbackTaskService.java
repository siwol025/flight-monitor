package com.siwol025.flight_monitor.global.fallback.service;

import com.siwol025.flight_monitor.global.fallback.domain.FallbackMonitoringTask;
import com.siwol025.flight_monitor.global.fallback.repository.FallbackMonitoringTaskRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FallbackTaskService {

    private final FallbackMonitoringTaskRepository fallbackRepository;

    @Transactional
    public String processPendingTask() {
        return fallbackRepository.findPendingTaskForUpdate()
                .map(task -> {
                    String payload = task.getPayload();
                    // 락이 걸린 상태에서 안전하게 삭제
                    fallbackRepository.delete(task);
                    log.warn("[FallbackTaskService] Redis 큐 다운. DB에서 안전하게 대체 작업을 가져옵니다.");
                    return payload;
                }).orElse(null);
    }

    @Transactional
    public void savePendingTasks(List<String> payloads) {
        List<FallbackMonitoringTask> tasks = payloads.stream()
                .map(payload -> FallbackMonitoringTask.builder()
                        .payload(payload)
                        .build())
                .toList();
        fallbackRepository.saveAll(tasks);
    }

    @Transactional(readOnly = true)
    public long countTasks() {
        return fallbackRepository.count();
    }

    @Transactional(readOnly = true)
    public boolean hasPendingTask() {
        return fallbackRepository.existsPendingTask();
    }
}
