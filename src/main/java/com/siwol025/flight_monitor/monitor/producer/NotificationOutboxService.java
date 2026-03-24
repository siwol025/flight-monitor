package com.siwol025.flight_monitor.monitor.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siwol025.flight_monitor.global.outbox.domain.Outbox;
import com.siwol025.flight_monitor.global.outbox.dto.OutboxEvent;
import com.siwol025.flight_monitor.global.outbox.repository.OutboxRepository;
import com.siwol025.flight_monitor.monitor.dto.PriceDropNotificationDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.mode", havingValue = "kafka", matchIfMissing = true)
public class NotificationOutboxService implements NotificationPublisher{

    private final OutboxRepository outboxRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    private static final String TOPIC_NAME = "flight-price-drop-events";

    @Override
    @Transactional
    public void publishPriceDrop(PriceDropNotificationDto priceDropNotificationDto) {
        try {
            String messageKey = String.valueOf(priceDropNotificationDto.flightId());
            String payload = objectMapper.writeValueAsString(priceDropNotificationDto);
            String eventType = priceDropNotificationDto.getClass().getName();

            Outbox outbox = outboxRepository.save(
                    Outbox.builder()
                            .topic(TOPIC_NAME)
                            .messageKey(messageKey)
                            .payload(payload)
                            .eventType(eventType)
                            .build()
            );

            eventPublisher.publishEvent(new OutboxEvent(
                    outbox.getId(),
                    outbox.getTopic(),
                    outbox.getMessageKey(),
                    outbox.getPayload(),
                    outbox.getEventType()
            ));

            log.info("📦 [Outbox] DB 저장 및 로컬 이벤트 발행 완료: 항공편 {}", priceDropNotificationDto.flightNumber());
        } catch (Exception e) {
            log.error("🚨 [Outbox] 저장 실패: 항공편 {}", priceDropNotificationDto.flightNumber(), e);
            throw new RuntimeException("Outbox 저장 실패로 인한 롤백", e);
        }
    }
}
