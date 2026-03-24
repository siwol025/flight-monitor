package com.siwol025.flight_monitor.global.outbox.domain;

public enum OutboxStatus {
    PENDING,  // 발송 대기 (초기 상태)
    SUCCESS,  // Kafka 발행 성공
    FAILED    // 발행 최종 실패 (Dead Letter)
}
