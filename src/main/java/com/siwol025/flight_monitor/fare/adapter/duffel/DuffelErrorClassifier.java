package com.siwol025.flight_monitor.fare.adapter.duffel;

import com.siwol025.flight_monitor.fare.OrderFailureKind;
import java.net.ConnectException;
import java.net.UnknownHostException;
import org.apache.hc.client5.http.ConnectTimeoutException;

/**
 * Duffel 주문·결제 호출 실패를 {@link OrderFailureKind}로 나눈다. 기준은 Duffel 재시도 지침이다:
 * 500·502는 재시도 금지(만들어졌을 수 있음), 503은 만들어지지 않은 것이 확실, 429는 한도 이후 재시도.
 */
final class DuffelErrorClassifier {

    private DuffelErrorClassifier() {
    }

    /** 응답을 받은 실패. code는 errors[0].code(본문을 해석할 수 없으면 null) */
    static OrderFailureKind classify(int status, String code) {
        if (status == 429) {
            return OrderFailureKind.RATE_LIMITED;
        }
        if (status == 503) {
            return OrderFailureKind.NOT_CREATED;
        }
        // 500·502·504와 모르는 5xx는 업체가 처리했는지 모른다
        if (status >= 500) {
            return OrderFailureKind.UNKNOWN;
        }
        if (status == 422) {
            return switch (code == null ? "" : code) {
                case "offer_no_longer_available", "not_found" -> OrderFailureKind.OFFER_UNAVAILABLE;
                case "expired" -> OrderFailureKind.OFFER_EXPIRED;
                case "offer_request_already_booked" -> OrderFailureKind.OFFER_ALREADY_BOOKED;
                default -> OrderFailureKind.REJECTED;
            };
        }
        // 401 인증, 403 권한, 400 요청 형식, 406 Accept, 그 밖의 4xx는 우리 설정·요청 문제
        return OrderFailureKind.CONFIGURATION;
    }

    /** 응답을 받지 못한 실패. 연결 전 실패만 보내지 않은 것이 확실하고, 그 밖(응답 타임아웃·연결 끊김)은 모른다 */
    static OrderFailureKind classify(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConnectException || cause instanceof ConnectTimeoutException
                    || cause instanceof UnknownHostException) {
                return OrderFailureKind.NOT_SENT;
            }
        }
        return OrderFailureKind.UNKNOWN;
    }
}
