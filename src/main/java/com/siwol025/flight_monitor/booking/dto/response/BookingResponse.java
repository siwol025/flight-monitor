package com.siwol025.flight_monitor.booking.dto.response;

import com.siwol025.flight_monitor.booking.domain.Booking;
import com.siwol025.flight_monitor.route.domain.SeatGrade;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record BookingResponse(
        Long bookingId,
        String orderId,
        String bookingReference,
        String departureAirport,
        String arrivalAirport,
        LocalDate departureDate,
        SeatGrade seatGrade,
        String carrierCode,
        String carrierName,
        String flightNumber,
        LocalDateTime departingAt,
        LocalDateTime arrivingAt,
        BigDecimal amount,
        String currency,
        String passengerName,
        LocalDateTime createdAt
) {
    public static BookingResponse from(Booking booking) {
        return new BookingResponse(booking.getId(), booking.getProviderOrderId(), booking.getBookingReference(),
                booking.getDepartureAirport(), booking.getArrivalAirport(), booking.getDepartureDate(),
                booking.getSeatGrade(), booking.getCarrierCode(), booking.getCarrierName(), booking.getFlightNumber(),
                booking.getDepartingAt(), booking.getArrivingAt(), booking.getAmount(), booking.getCurrency(),
                booking.getGivenName() + " " + booking.getFamilyName(), booking.getCreatedAt());
    }
}
