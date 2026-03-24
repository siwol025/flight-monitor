package com.siwol025.flight_monitor.global.outbox.dto;

public record OutboxEvent(
        Long outboxId,
        String topic,
        String messageKey,
        String payload,
        String eventType
) {}
