package com.siwol025.flight_monitor.monitor.producer;

import com.siwol025.flight_monitor.monitor.utils.TaskQueueManager;
import com.siwol025.flight_monitor.subscription.service.SubscriptionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!mock")
public class FlightMonitoringProducer {

    private final SubscriptionService subscriptionService;
    private final TaskQueueManager taskQueueManager;

    // 주기 T와 독립적인 파라미터 — T=60s 전환 시에도 유지. MAX_QUEUE_THRESHOLD는 컨슈머가 밀릴 때
    // 큐 무한 증가를 막는 부하흘림(load-shed) 상한(주기가 짧아지면 더 자주 점검될 뿐), CHUNK_SIZE는
    // 발행 배치 크기다. 실용량 기준 정확한 값은 측정 단계에서 재확정한다.
    private static final int MAX_QUEUE_THRESHOLD = 20000;
    private static final int CHUNK_SIZE = 1000;

    @Scheduled(fixedRateString = "${monitor.produce.fixed-rate-ms}")
    @SchedulerLock(
            name = "produceMonitoringTasks",
            lockAtLeastFor = "${monitor.produce.lock-at-least-for}",
            lockAtMostFor = "${monitor.produce.lock-at-most-for}"
    )
    public void produceMonitoringTasks() {
        Long currentQueueSize = taskQueueManager.getQueueSizeSafely();

        if (currentQueueSize != null && currentQueueSize > MAX_QUEUE_THRESHOLD) {
            log.warn("[FlightMonitoringProducer] 큐 대기 작업 임계치 초과 (현재: {}건). 발행 중단", currentQueueSize);
            return;
        }

        List<Long> targets = subscriptionService.getActiveRouteIds();
        if (targets.isEmpty()) {
            return;
        }

        for (int i = 0; i < targets.size(); i += CHUNK_SIZE) {
            int end = Math.min(i+CHUNK_SIZE, targets.size());
            List<Long> chunk = targets.subList(i, end);

            // 큐 페이로드는 노선 ID 숫자 문자열이다
            List<String> payloads = chunk.stream()
                    .map(String::valueOf)
                    .toList();

            taskQueueManager.publishTasks(payloads);
        }
    }
}
