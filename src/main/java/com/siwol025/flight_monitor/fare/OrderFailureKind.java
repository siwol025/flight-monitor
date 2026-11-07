package com.siwol025.flight_monitor.fare;

/**
 * 업체 주문·결제 호출 실패의 종류. 호출한 쪽은 이것으로 재요청해도 되는지 판단한다.
 */
public enum OrderFailureKind {

    /** 요청이 처리됐는지 모른다(500·502·504, 응답 타임아웃, 응답 전 연결 끊김). 재요청하면 중복될 수 있다 */
    UNKNOWN,
    /** 요청을 보내기 전에 실패했다(연결 실패). 재요청해도 된다 */
    NOT_SENT,
    /** 호출 한도 초과(429). 만들어지지 않았으므로 나중에 재요청해도 된다 */
    RATE_LIMITED,
    /** 업체가 만들지 않았다고 확인한 일시 장애(503). 재요청해도 된다 */
    NOT_CREATED,
    /** 매진·없는 offer */
    OFFER_UNAVAILABLE,
    /** 만료된 offer */
    OFFER_EXPIRED,
    /** 이미 주문된 offer */
    OFFER_ALREADY_BOOKED,
    /** 그 밖에 업체가 처리를 거절한 요청(422). 업체 code를 보고 판단한다 */
    REJECTED,
    /** 인증·권한·요청 형식 등 우리 설정 문제 */
    CONFIGURATION
}
