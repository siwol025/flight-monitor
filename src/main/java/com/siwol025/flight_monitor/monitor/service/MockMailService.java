package com.siwol025.flight_monitor.monitor.service;

import com.siwol025.flight_monitor.global.exception.ErrorTag;
import com.siwol025.flight_monitor.global.exception.custom.BadRequestException;
import com.siwol025.flight_monitor.mock.mail.config.MockMailProperties;
import com.siwol025.flight_monitor.monitor.domain.NotificationHistory;
import com.siwol025.flight_monitor.monitor.dto.EmailSendTaskDto;
import com.siwol025.flight_monitor.monitor.repository.NotificationHistoryRepository;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
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

    // 첫 발송 시각. 장애 구간을 실행 시각과 무관하게 재현하기 위한 기준점 (컨테이너 재생성 시 초기화)
    private final AtomicLong firstSendAt = new AtomicLong();

    public void sendPriceDropNotification(EmailSendTaskDto taskDto) {
        firstSendAt.compareAndSet(0, System.currentTimeMillis());
        simulateLatency();
        simulateOutage();
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

    // SMTP 서버 다운처럼 일정 시간 동안 모든 발송이 실패하는 상황
    private void simulateOutage() {
        if (properties.getOutageDurationMs() <= 0) {
            return;
        }
        long elapsed = System.currentTimeMillis() - firstSendAt.get();
        long start = properties.getOutageStartAfterMs();
        if (elapsed >= start && elapsed < start + properties.getOutageDurationMs()) {
            throw new MailSendException("[MockMailService] 주입된 장애 구간 오류");
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
