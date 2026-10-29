package com.siwol025.flight_monitor.fare;

/**
 * 우리 쪽 설정·요청 문제(인증 실패, 요청 형식, 경로·권한). 노선과 무관하므로 노선을 무효로 돌리지 않고 에러 로그로 드러낸다.
 */
public class FareProviderConfigurationException extends FareProviderException {

    public FareProviderConfigurationException(String message) {
        super(message);
    }
}
