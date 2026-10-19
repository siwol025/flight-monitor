package com.siwol025.flight_monitor.monitor.service;

import com.siwol025.flight_monitor.global.exception.custom.BadRequestException;
import com.siwol025.flight_monitor.mock.mail.config.MockMailProperties;
import com.siwol025.flight_monitor.monitor.domain.NotificationHistory;
import com.siwol025.flight_monitor.monitor.dto.EmailSendTaskDto;
import com.siwol025.flight_monitor.monitor.repository.NotificationHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MockMailServiceTest {

    @Mock
    private NotificationHistoryRepository historyRepository;

    private MockMailProperties properties;
    private MockMailService mockMailService;

    private final EmailSendTaskDto dto = new EmailSendTaskDto("user@test.com", "제목", "내용");

    @BeforeEach
    void setUp() {
        properties = new MockMailProperties();
        mockMailService = new MockMailService(historyRepository, properties);
    }

    @Test
    void sendPriceDropNotification_실패율0_발송이력저장됨() {
        mockMailService.sendPriceDropNotification(dto);

        verify(historyRepository).save(any(NotificationHistory.class));
    }

    @Test
    void sendPriceDropNotification_실패율1_영구오류비율0_일시오류발생하고_저장안됨() {
        properties.setFailureRate(1.0);
        properties.setPermanentFailureRatio(0.0);

        assertThrows(MailSendException.class, () -> mockMailService.sendPriceDropNotification(dto));
        verify(historyRepository, never()).save(any());
    }

    @Test
    void sendPriceDropNotification_실패율1_영구오류비율1_영구오류발생하고_저장안됨() {
        properties.setFailureRate(1.0);
        properties.setPermanentFailureRatio(1.0);

        assertThrows(BadRequestException.class, () -> mockMailService.sendPriceDropNotification(dto));
        verify(historyRepository, never()).save(any());
    }

    @Test
    void sendPriceDropNotification_지연설정_지연시간만큼대기후저장됨() {
        properties.setLatencyMs(50);

        long start = System.nanoTime();
        mockMailService.sendPriceDropNotification(dto);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs).isGreaterThanOrEqualTo(50);
        verify(historyRepository).save(any(NotificationHistory.class));
    }
}
