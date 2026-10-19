package com.siwol025.flight_monitor.monitor.service;

import com.siwol025.flight_monitor.global.exception.ErrorTag;
import com.siwol025.flight_monitor.global.exception.custom.BadRequestException;
import com.siwol025.flight_monitor.mock.mail.config.MockMailProperties;
import com.siwol025.flight_monitor.monitor.domain.NotificationHistory;
import com.siwol025.flight_monitor.monitor.dto.EmailSendTaskDto;
import com.siwol025.flight_monitor.monitor.repository.NotificationHistoryRepository;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailSendException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile({"dev", "docker"})
@RequiredArgsConstructor
public class MockMailService implements NotificationService{

    private final NotificationHistoryRepository historyRepository;
    private final MockMailProperties properties;

    public void sendPriceDropNotification(EmailSendTaskDto taskDto) {
        simulateLatency();
        simulateFailure();

        NotificationHistory history = NotificationHistory.builder()
                .subject(taskDto.subject())
                .content(taskDto.content())
                .toEmail(taskDto.toEmail())
                .build();
        historyRepository.save(history);
    }

    private void simulateLatency() {
        if (properties.getLatencyMs() <= 0) {
            return;
        }
        try {
            Thread.sleep(properties.getLatencyMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // 운영 MailService가 던지는 예외 타입과 맞춘다: SMTP 장애(일시) / 잘못된 주소(영구)
    private void simulateFailure() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (random.nextDouble() >= properties.getFailureRate()) {
            return;
        }
        if (random.nextDouble() < properties.getPermanentFailureRatio()) {
            throw new BadRequestException(ErrorTag.INVALID_INPUT);
        }
        throw new MailSendException("[MockMailService] 주입된 일시 오류");
    }
}
