package com.siwol025.flight_monitor.notification.producer;

/**
 * 가격 하락 이벤트가 브로커에 전달됐다고 확인받지 못함(확인 거부·라우팅 실패·확인 대기 시간 초과·전송 실패).
 * 호출자는 최신가를 전진시키지 않고 다음 감시 주기에 다시 감지되게 둔다.
 */
public class PriceDropPublishException extends RuntimeException {

    public PriceDropPublishException(String message) {
        super(message);
    }

    public PriceDropPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
