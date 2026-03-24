package com.siwol025.flight_monitor.global.outbox.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siwol025.flight_monitor.global.outbox.domain.Outbox;
import com.siwol025.flight_monitor.global.outbox.domain.OutboxStatus;
import com.siwol025.flight_monitor.global.outbox.repository.OutboxRepository;
import com.siwol025.flight_monitor.global.outbox.service.OutboxStatusUpdater;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRetryScheduler {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final OutboxStatusUpdater statusUpdater;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 60000)
    public void retryPendingMessages() {
        LocalDateTime oneMinuteAgo = LocalDateTime.now().minusMinutes(1);

        List<Outbox> pendingOutboxes = outboxRepository.findByStatusAndCreatedAtBefore(
                OutboxStatus.PENDING,
                oneMinuteAgo
        );

        if (pendingOutboxes.isEmpty()) {
            return;
        }

        log.info("🔄 [Outbox Scheduler] {}개의 전송 누락 메시지 재시도를 시작합니다.", pendingOutboxes.size());

        for (Outbox outbox : pendingOutboxes) {
            try {
                // 1. JSON 페이로드를 원본 객체로 복원
                Class<?> clazz = Class.forName(outbox.getEventType());
                Object originalDto = objectMapper.readValue(outbox.getPayload(), clazz);

                // 2. 카프카로 재전송 시도
                kafkaTemplate.send(outbox.getTopic(), outbox.getMessageKey(), originalDto)
                        .whenComplete((result, ex) -> {
                            if (ex == null) {
                                statusUpdater.markAsSuccess(outbox.getId());
                                log.info("✅ [Outbox Scheduler] 재전송 성공: ID {}", outbox.getId());
                            } else {
                                log.error("🚨 [Outbox Scheduler] 재전송 실패: ID {}", outbox.getId(), ex);
                            }
                        });

            } catch (Exception e) {
                log.error("🚨 [Outbox Scheduler] 페이로드 복원 실패로 재전송 불가: ID {}", outbox.getId(), e);
            }
        }
    }
}
