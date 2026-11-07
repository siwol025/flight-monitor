package com.siwol025.flight_monitor.payment;

import lombok.Getter;

/**
 * 결제사 승인·조회 실패. code·providerMessage는 결제사 오류 code와 안내 문구다(응답이 없거나 해석할 수 없으면 null).
 * 메시지에 시크릿 키·인증 헤더를 싣지 않는다.
 */
@Getter
public class PaymentGatewayException extends RuntimeException {

    private final PaymentFailureKind kind;
    private final String code;
    private final String providerMessage;

    public PaymentGatewayException(PaymentFailureKind kind, String code, String providerMessage, String message) {
        super(message);
        this.kind = kind;
        this.code = code;
        this.providerMessage = providerMessage;
    }

    public PaymentGatewayException(PaymentFailureKind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.code = null;
        this.providerMessage = null;
    }
}
