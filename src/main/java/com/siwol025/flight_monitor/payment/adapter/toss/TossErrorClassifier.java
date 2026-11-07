package com.siwol025.flight_monitor.payment.adapter.toss;

import com.siwol025.flight_monitor.payment.PaymentFailureKind;

/**
 * 토스페이먼츠 승인·조회 실패를 {@link PaymentFailureKind}로 나눈다. 기준은 실측(1·2단계)과 승인 오류 목록이다.
 * 승인됐을 수 있는 응답은 모두 UNKNOWN이다: 호출한 쪽이 조회로 확인한 뒤에만 재요청한다.
 */
final class TossErrorClassifier {

    private TossErrorClassifier() {
    }

    /** 응답을 받은 실패. code는 본문 code(빈 본문·code 없는 본문이면 null) */
    static PaymentFailureKind classify(int status, String code) {
        if (status >= 500 || status == 409) {
            return PaymentFailureKind.UNKNOWN;
        }
        if (code == null) {
            // 401, 게이트웨이 인증 거절(빈 본문 403), 없는 경로(빈 본문 404)
            return PaymentFailureKind.CONFIGURATION;
        }
        return switch (code) {
            // 이미 승인됐거나 처리 중일 수 있다. PROVIDER_ERROR·CARD_PROCESSING_ERROR·UNAPPROVED_ORDER_ID는 결제사가 "조회 후 판단"으로 둔 오류
            case "ALREADY_PROCESSED_PAYMENT", "ALREADY_PROCESSING_REQUEST", "IDEMPOTENT_REQUEST_PROCESSING",
                 "PROVIDER_ERROR", "CARD_PROCESSING_ERROR", "UNAPPROVED_ORDER_ID" -> PaymentFailureKind.UNKNOWN;
            case "EXPIRED_PAYMENT", "NOT_FOUND_PAYMENT_SESSION" -> PaymentFailureKind.EXPIRED;
            // 금액·주문번호가 결제창과 다르다(실측 B_02). 승인되지 않았다
            case "FDS_ERROR", "FORBIDDEN_REQUEST" -> PaymentFailureKind.REJECTED;
            default -> isCardRejection(code) ? PaymentFailureKind.REJECTED : PaymentFailureKind.CONFIGURATION;
        };
    }

    // REJECT_CARD_PAYMENT·REJECT_CARD_COMPANY, INVALID_CARD_NUMBER·INVALID_REJECT_CARD·INVALID_STOPPED_CARD 등, 한도 초과
    private static boolean isCardRejection(String code) {
        return code.startsWith("REJECT_CARD_") || (code.startsWith("INVALID_") && code.contains("CARD"))
                || code.startsWith("EXCEED_");
    }
}
