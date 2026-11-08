package com.siwol025.flight_monitor.booking.messaging;

/**
 * 결제 명령이 결제 큐에 들어갔다고 브로커에게 확인받지 못함(확인 거부·라우팅 실패·확인 대기 시간 초과·전송 실패).
 * 승인은 이미 커밋됐으므로 호출자는 로그만 남기고, 멈춘 PAYMENT_APPROVED는 확인 스케줄러가 다시 발행한다.
 */
public class DuffelPaymentPublishException extends RuntimeException {

    public DuffelPaymentPublishException(String message) {
        super(message);
    }

    public DuffelPaymentPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
