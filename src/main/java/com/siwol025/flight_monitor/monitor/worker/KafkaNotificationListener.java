package com.siwol025.flight_monitor.monitor.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siwol025.flight_monitor.monitor.dto.EmailSendTaskDto;
import com.siwol025.flight_monitor.monitor.dto.PriceDropNotificationDto;
import com.siwol025.flight_monitor.subscription.service.SubscriptionService;
import com.siwol025.flight_monitor.user.dto.UserEmailDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.mode", havingValue = "kafka")
@Profile("!mock")
public class KafkaNotificationListener {

    private final SubscriptionService subscriptionService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String OUT_TOPIC = "email-send-tasks";

    @KafkaListener(topics = "flight-price-drop-events", groupId = "notification-fanout-group", concurrency = "3")
    public void processNotification(PriceDropNotificationDto eventDto) {
        try {
            List<UserEmailDto> subscribers = subscriptionService.getSubscribers(eventDto.flightId());

            if (subscribers.isEmpty()) {
                return;
            }

            String subject = createSubject(eventDto.flightNumber());
            String content = createContent(eventDto);

            for (UserEmailDto user : subscribers) {
                EmailSendTaskDto taskDto = new EmailSendTaskDto(user.email(), subject, content);
                try {
                    kafkaTemplate.send(OUT_TOPIC, eventDto.flightNumber() + user.email(), taskDto);
                } catch (Exception e) {
                    log.error("🚨 [Kafka] 특정 유저 이메일 작업 전송 실패: {}", user.email(), e);
                }
            }
        } catch (Exception e) {
            log.error("🚨 알림 분배 처리 중 오류 발생", e);
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
