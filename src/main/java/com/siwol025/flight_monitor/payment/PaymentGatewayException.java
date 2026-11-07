package com.siwol025.flight_monitor.payment;

import lombok.Getter;

/**
 * 결제사 승인·조회 실패. code·providerMessage는 결제사 오류 code와 안내 문구다(응답이 없거나 해석할 수 없으면 null).
 * httpStatus는 받은 응답의 HTTP 상태다(응답을 받지 못했으면 null). 같은 멱등키가 5xx를 재생하는지 판단할 때 쓴다.
 * 메시지에 시크릿 키·인증 헤더를 싣지 않는다.
 */
@Getter
public class PaymentGatewayException extends RuntimeException {

    private final PaymentFailureKind kind;
    private final String code;
    private final String providerMessage;
    private final Integer httpStatus;

    public PaymentGatewayException(PaymentFailureKind kind, int httpStatus, String code, String providerMessage,
                                   String message) {
        super(message);
        this.kind = kind;
        this.code = code;
        this.providerMessage = providerMessage;
        this.httpStatus = httpStatus;
    }

    public PaymentGatewayException(PaymentFailureKind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.code = null;
        this.providerMessage = null;
        this.httpStatus = null;
    }
}
