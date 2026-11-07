package com.siwol025.flight_monitor.booking.dto.response;

import com.siwol025.flight_monitor.booking.domain.BookingStatus;

/** 결과를 아직 모르는 요청의 202 응답. 진행은 예매 조회로 본다(확인은 스케줄러) */
public record BookingStatusResponse(long bookingId, BookingStatus status) {
}
