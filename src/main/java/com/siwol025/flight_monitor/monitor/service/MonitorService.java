package com.siwol025.flight_monitor.monitor.service;

import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;

public interface MonitorService {

    void checkAndUpdatePrice(Long flightId, SeatGrade seatGrade);
}
