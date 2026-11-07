package com.siwol025.flight_monitor.fare;

import lombok.Getter;

/**
 * 업체 주문·결제 호출 실패. code는 업체 오류 code(응답이 없거나 해석할 수 없으면 null)다.
 */
@Getter
public class FlightOrderException extends RuntimeException {

    private final OrderFailureKind kind;
    private final String code;

    public FlightOrderException(OrderFailureKind kind, String code, String message) {
        super(message);
        this.kind = kind;
        this.code = code;
    }

    public FlightOrderException(OrderFailureKind kind, String code, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.code = code;
    }
}
