package com.siwol025.flight_monitor.booking.idempotency;

/**
 * 이 서버가 처리권을 가진 멱등키.
 *
 * @param keyId     멱등키 행 ID(예매를 만들면 {@link IdempotencyService#attachBooking}에 넘긴다)
 * @param bookingId 멈춘 처리를 재선점했고 그 처리가 이미 예매를 만들었으면 그 예매 ID. 이때는 외부 호출 없이 그 예매의
 *                  현재 상태를 응답한다(확인은 스케줄러). 처음 온 키이거나 예매 전에 멈췄으면 null(처음부터 처리)
 */
public record IdempotencyClaim(long keyId, Long bookingId) {
}
