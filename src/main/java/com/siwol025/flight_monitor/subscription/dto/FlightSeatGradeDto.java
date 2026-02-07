package com.siwol025.flight_monitor.subscription.dto;

import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;

public record FlightSeatGradeDto(Long flightId, SeatGrade seatGrade) {
}
