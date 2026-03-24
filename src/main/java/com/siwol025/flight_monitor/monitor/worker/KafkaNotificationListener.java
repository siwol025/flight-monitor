package com.siwol025.flight_monitor.monitor.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siwol025.flight_monitor.global.outbox.domain.Outbox;
import com.siwol025.flight_monitor.global.outbox.dto.OutboxEvent;
import com.siwol025.flight_monitor.global.outbox.repository.OutboxRepository;
import com.siwol025.flight_monitor.monitor.dto.EmailSendTaskDto;
import com.siwol025.flight_monitor.monitor.dto.PriceDropNotificationDto;
import com.siwol025.flight_monitor.subscription.service.SubscriptionService;
import com.siwol025.flight_monitor.user.dto.UserEmailDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.mode", havingValue = "kafka")
public class KafkaNotificationListener {

    private final SubscriptionService subscriptionService;
    private final ApplicationEventPublisher eventPublisher;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    private static final String OUT_TOPIC = "email-send-tasks";

    @KafkaListener(topics = "flight-price-drop-events", groupId = "notification-fanout-group")
    @Transactional
    public void processNotification(PriceDropNotificationDto eventDto) {
        try {
            log.info("📩 [Kafka Consumer] 알림 처리 시작: 항공편 ID: {}", eventDto.flightNumber());
            List<UserEmailDto> subscribers = subscriptionService.getSubscribers(eventDto.flightId());

            if (subscribers.isEmpty()) {
                log.info("ℹ️ [Kafka Consumer] 구독자가 없습니다. 항공편: {}", eventDto.flightNumber());
                return;
            }

            String subject = createSubject(eventDto.flightNumber());
            String content = createContent(eventDto);

            List<Outbox> outboxes = subscribers.stream()
                    .map(user -> {
                        EmailSendTaskDto taskDto = new EmailSendTaskDto(user.email(), subject, content);
                        try {
                            return Outbox.builder()
                                    .topic(OUT_TOPIC)
                                    .messageKey(user.email())
                                    .payload(objectMapper.writeValueAsString(taskDto))
                                    .eventType(taskDto.getClass().getName())
                                    .build();
                        } catch (Exception e) {
                            throw new RuntimeException("JSON 직렬화 실패", e);
                        }
                    }).toList();

            outboxRepository.saveAll(outboxes);

            outboxes.forEach(outbox ->
                    eventPublisher.publishEvent(
                            new OutboxEvent(
                                    outbox.getId(),
                                    outbox.getTopic(),
                                    outbox.getMessageKey(),
                                    outbox.getPayload(),
                                    outbox.getEventType()
                            )
                    )
            );

            log.info("✅ DB Outbox 저장 및 로컬 이벤트 발행 완료: 총 {}건", subscribers.size());
        } catch (Exception e) {
            log.error("🚨 알림 분배 처리 중 오류 발생. 트랜잭션 롤백됨.", e);
            throw e;
        }
    }

    private String createContent(PriceDropNotificationDto dto) {
        return String.format(
                "항공편 ID: %s\n좌석: %s\n가격 변동: %s -> %s",
                dto.flightNumber(), dto.seatGrade(), dto.oldPrice(), dto.newPrice()
        );
    }

    private String createSubject(String flightNumber) {
        return String.format("✈️ 항공편 ID: %s 가격 하락 알림!", flightNumber);
    }
}
