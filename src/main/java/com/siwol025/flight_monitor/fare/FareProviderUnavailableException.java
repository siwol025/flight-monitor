package com.siwol025.flight_monitor.fare;

/**
 * 업체 쪽 일시 장애(5xx, 타임아웃, 연결 실패). 이번 결과는 저장하지 않고 다음 주기에 다시 시도한다.
 */
public class FareProviderUnavailableException extends FareProviderException {

    public FareProviderUnavailableException(String message) {
        super(message);
    }

    public FareProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
