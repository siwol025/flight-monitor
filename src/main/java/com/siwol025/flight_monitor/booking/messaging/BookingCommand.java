package com.siwol025.flight_monitor.booking.messaging;

/** 예매 처리 명령. 상태는 소비자가 DB에서 읽으므로 예매 ID만 담는다 */
public record BookingCommand(long bookingId) {
}
