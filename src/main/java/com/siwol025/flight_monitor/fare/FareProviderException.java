package com.siwol025.flight_monitor.fare;

/**
 * 외부 운임 업체 호출 실패의 공통 상위 예외. 파이프라인은 하위 타입별로 처리를 나눈다
 * (한도 초과 → 전체 정지, 업체 장애 → 다음 주기, 잘못된 노선 → 노선 무효, 설정 오류 → 에러 로그).
 */
public abstract class FareProviderException extends RuntimeException {

    protected FareProviderException(String message) {
        super(message);
    }

    protected FareProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
