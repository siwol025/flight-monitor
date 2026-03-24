package com.siwol025.flight_monitor.global.outbox.service;

import com.siwol025.flight_monitor.global.outbox.domain.OutboxStatus;
import com.siwol025.flight_monitor.global.outbox.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OutboxStatusUpdater {

    private final OutboxRepository outboxRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsSuccess(Long outboxId) {
        outboxRepository.updateStatus(outboxId, OutboxStatus.SUCCESS);
    }
}
