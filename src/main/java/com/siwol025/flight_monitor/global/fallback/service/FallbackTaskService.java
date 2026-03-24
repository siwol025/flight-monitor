package com.siwol025.flight_monitor.global.fallback.service;

import com.siwol025.flight_monitor.global.fallback.repository.FallbackMonitoringTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                    return payload;
                }).orElse(null);
    }
}
