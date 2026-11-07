package com.siwol025.flight_monitor.payment.adapter.toss;

import com.siwol025.flight_monitor.payment.PaymentFailureKind;
import java.util.Set;

/**
 * 토스페이먼츠 승인·조회 실패를 {@link PaymentFailureKind}로 나눈다. 기준은 실측(1·2단계)과 승인 오류 목록이다.
 * 승인됐을 수 있는 응답은 모두 UNKNOWN이다: 호출한 쪽이 조회로 확인한 뒤에만 재요청한다.
 */
final class TossErrorClassifier {

    private static final Set<String> CUSTOMER_CARD_FAILURES = Set.of("INVALID_PASSWORD", "BELOW_MINIMUM_AMOUNT",
            "NOT_AVAILABLE_PAYMENT", "NOT_ALLOWED_POINT_USE", "INVALID_AUTHORIZE_AUTH");

    private TossErrorClassifier() {
    }

    /** 응답을 받은 실패. code는 본문 code(빈 본문·code 없는 본문이면 null) */
    static PaymentFailureKind classify(int status, String code) {
        // 429는 처리하지 않았다는 응답이지만, 승인 재요청은 조회로 확인한 뒤에만 하는 같은 경로로 보낸다
        if (status >= 500 || status == 409 || status == 429) {
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

    // REJECT_CARD_PAYMENT·REJECT_CARD_COMPANY, INVALID_CARD_NUMBER·INVALID_REJECT_CARD·INVALID_STOPPED_CARD 등, 한도 초과,
    // 카드·가맹점이 지원하지 않는 할부(NOT_SUPPORTED_*)와 그 밖의 고객 카드 문제(스펙 5-2절 "실패 처리"). 청구되지 않았다
    private static boolean isCardRejection(String code) {
        return code.startsWith("REJECT_CARD_") || (code.startsWith("INVALID_") && code.contains("CARD"))
                || code.startsWith("EXCEED_") || code.startsWith("NOT_SUPPORTED_")
                || CUSTOMER_CARD_FAILURES.contains(code);
    }
}
