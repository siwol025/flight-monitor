package com.siwol025.flight_monitor.monitor.worker;

import com.siwol025.flight_monitor.monitor.dto.EmailSendTaskDto;
import com.siwol025.flight_monitor.monitor.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.mode", havingValue = "kafka")
@Profile("!mock")
public class KafkaEmailTaskListener {

    private final NotificationService notificationService;

    @KafkaListener(topics = "email-send-tasks", groupId = "email-sender-group", concurrency = "50")
    public void processEmailTask(EmailSendTaskDto taskDto) {
        try {
            notificationService.sendPriceDropNotification(taskDto);
            log.info("[KafkaEmailTaskListener] 알림 전송 완료");

        } catch (Exception e) {
            log.error("[KafkaEmailTaskListener] 개별 이메일 발송 처리 중 예외 발생", e);
        }
    }
}
