package com.siwol025.flight_monitor.monitor.worker;

import com.siwol025.flight_monitor.monitor.dto.EmailSendTaskDto;
import com.siwol025.flight_monitor.monitor.dto.PriceDropNotificationDto;
import com.siwol025.flight_monitor.subscription.service.SubscriptionService;
import com.siwol025.flight_monitor.user.dto.UserEmailDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.mode", havingValue = "kafka")
public class KafkaNotificationListener {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final SubscriptionService subscriptionService;

    private static final String OUT_TOPIC = "email-send-tasks";

    @KafkaListener(topics = "flight-price-drop-events", groupId = "notification-fanout-group")
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

            for (UserEmailDto user : subscribers) {
                EmailSendTaskDto taskDto = new EmailSendTaskDto(user.email(), subject, content);
                kafkaTemplate.send(OUT_TOPIC, user.email(), taskDto);
            }

            log.info("✅ 항공편 {}, 생성된 발송 작업 {}건을 토픽({})으로 발행 완료",
                    eventDto.flightId(), subscribers.size(), OUT_TOPIC);

        } catch (Exception e) {
            log.error("🚨 알림 Worker 처리 중 오류 발생", e);
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
