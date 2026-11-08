package com.siwol025.flight_monitor.booking.dto.response;

import com.siwol025.flight_monitor.booking.domain.Booking;
import com.siwol025.flight_monitor.booking.domain.BookingStatus;
import java.time.LocalDateTime;

/** 운영자 예매 목록 한 줄(읽기 전용). orderId는 Duffel 주문 ID, statusChangedAt은 이 상태가 된 DB 시각(UTC) */
public record AdminBookingResponse(
        long bookingId,
        BookingStatus status,
        LocalDateTime statusChangedAt,
        String orderId,
        String failureCode
) {
    public static AdminBookingResponse from(Booking booking) {
        return new AdminBookingResponse(booking.getId(), booking.getStatus(), booking.getStatusChangedAt(),
                booking.getProviderOrderId(), booking.getFailureCode());
    }
}
