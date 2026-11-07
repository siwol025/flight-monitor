package com.siwol025.flight_monitor.booking.idempotency;

public enum IdempotencyStatus {
    /** 2xx 응답 저장(외부 결과를 모르는 202 포함) */
    COMPLETED,
    /** 확정 4xx 응답 저장 */
    FAILED,
    /** 한 서버가 선점해 처리 중 */
    PROCESSING
}
