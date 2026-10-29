package com.siwol025.flight_monitor.monitor.producer;

import com.siwol025.flight_monitor.monitor.utils.TaskQueueManager;
import com.siwol025.flight_monitor.subscription.service.SubscriptionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Profile("!mock")
public class FlightMonitoringProducer {

    private final SubscriptionService subscriptionService;
    private final TaskQueueManager taskQueueManager;

    // 발행 배치 크기(주기 T와 독립). 큐는 ZADD NX라 소비가 밀려도 같은 노선이 쌓이지 않으므로(큐 길이 ≤ 활성 노선 수)
    // 큐 길이를 보고 발행을 건너뛰지 않는다.
    private static final int CHUNK_SIZE = 1000;

    @Scheduled(fixedRateString = "${monitor.produce.fixed-rate-ms}")
    @SchedulerLock(
            name = "produceMonitoringTasks",
            lockAtLeastFor = "${monitor.produce.lock-at-least-for}",
            lockAtMostFor = "${monitor.produce.lock-at-most-for}"
    )
    public void produceMonitoringTasks() {
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
