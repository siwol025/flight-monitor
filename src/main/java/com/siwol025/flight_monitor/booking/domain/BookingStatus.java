package com.siwol025.flight_monitor.booking.domain;

/**
 * 예매 진행 상태. 상태 변경은 조건부 UPDATE(BookingRepository)로만 한다(서버가 여럿이어도 한 곳만 바꾼다).
 */
public enum BookingStatus {
    /** 예매 행을 만들고 hold 호출 직전·도중. hold 결과를 모르면 여기서 보류(확인 스케줄러가 조회로만 확인) */
    HOLD_REQUESTED,
    /** hold 주문 생성, 고객 결제 대기 */
    HELD,
    /** Toss 승인 호출 직전·도중. 결과 모름 포함(조회로 확인한 뒤에만 재승인) */
    PAYMENT_CONFIRMING,
    /** Toss 승인 완료, Duffel 결제 대기 */
    PAYMENT_APPROVED,
    /** 소비자가 Duffel 결제를 보내기 직전 기록. 이 상태에서 다시 시작되면 결과를 모르는 것으로 본다 */
    DUFFEL_PAYING,
    /** 확인 대기(보류). Duffel 결제 결과를 모름. 재결제 없이 읽기 전용 조회만 한다 */
    DUFFEL_PAYMENT_UNCONFIRMED,
    /** 항공권 번호 확인(성공) */
    TICKETED,
    /** hold 확정 실패. 돈을 받기 전(종료) */
    FAILED
}
