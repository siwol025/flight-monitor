package com.siwol025.flight_monitor.global.outbox.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siwol025.flight_monitor.global.outbox.dto.OutboxEvent;
import com.siwol025.flight_monitor.global.outbox.service.OutboxStatusUpdater;
import com.siwol025.flight_monitor.monitor.dto.PriceDropNotificationDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventListener {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final OutboxStatusUpdater statusUpdater;
    private final ObjectMapper objectMapper;

    @Async("outboxEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOutboxEvent(OutboxEvent event) {
        log.info("📤 [Outbox Listener] Kafka 전송 시도: Outbox ID {}", event.outboxId());

        try {
            Class<?> clazz = Class.forName(event.eventType());
            Object eventDto = objectMapper.readValue(event.payload(), clazz);

            kafkaTemplate.send(event.topic(), event.messageKey(), eventDto)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            statusUpdater.markAsSuccess(event.outboxId());
                            log.info("🚀 [Kafka] 전송 성공 및 Outbox 갱신 완료: {}", event.messageKey());
                        } else {
                            // 전송 실패 시 DB는 PENDING 상태 유지, 스케줄러가 나중에 처리함
                            log.error("🚨 [Kafka] 비동기 전송 실패: Outbox ID {}", event.outboxId(), ex);
                        }
                    });
        } catch (Exception e) {
            log.error("Outbox 처리 중 에러 발생: {}", e.getMessage(), e);
        }
    }
}
