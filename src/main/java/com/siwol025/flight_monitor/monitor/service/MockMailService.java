package com.siwol025.flight_monitor.monitor.service;

import com.siwol025.flight_monitor.monitor.domain.NotificationHistory;
import com.siwol025.flight_monitor.monitor.dto.EmailSendTaskDto;
import com.siwol025.flight_monitor.monitor.repository.NotificationHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Profile({"dev", "docker"})
@RequiredArgsConstructor
public class MockMailService implements NotificationService{

    private final NotificationHistoryRepository historyRepository;

    //@Transactional
    public void sendPriceDropNotification(EmailSendTaskDto taskDto) {
        NotificationHistory history = NotificationHistory.builder()
                .subject(taskDto.subject())
                .content(taskDto.content())
                .toEmail(taskDto.toEmail())
                .build();
        historyRepository.save(history);
    }
}
