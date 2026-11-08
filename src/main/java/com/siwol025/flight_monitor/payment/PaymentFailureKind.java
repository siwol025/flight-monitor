package com.siwol025.flight_monitor.payment;

/**
 * 결제사 승인·조회 실패의 종류. 호출한 쪽은 이것으로 재요청해도 되는지 판단한다.
 */
public enum PaymentFailureKind {

    /** 카드 거절 등 결제사가 승인을 거절했다. 청구되지 않았다 */
    REJECTED,
    /** 승인 기한이 지났거나 결제 세션이 없다. 다시 결제해야 한다 */
    EXPIRED,
    /** 승인됐는지 모른다(타임아웃, 5xx, 처리 중, 이미 처리됨). 조회로 확인한 뒤에만 재요청한다 */
    UNKNOWN,
    /** 인증·요청 형식 등 우리 설정 문제 */
    CONFIGURATION
}
