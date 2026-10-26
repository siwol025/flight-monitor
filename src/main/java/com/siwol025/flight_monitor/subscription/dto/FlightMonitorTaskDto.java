package com.siwol025.flight_monitor.subscription.dto;

import com.siwol025.flight_monitor.route.domain.SeatGrade;

public record FlightMonitorTaskDto(Long flightId, SeatGrade seatGrade) {
}
