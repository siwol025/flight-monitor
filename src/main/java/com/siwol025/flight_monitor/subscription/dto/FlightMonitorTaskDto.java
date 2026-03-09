package com.siwol025.flight_monitor.subscription.dto;

import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;

public record FlightMonitorTaskDto(Long flightId, SeatGrade seatGrade) {
}
