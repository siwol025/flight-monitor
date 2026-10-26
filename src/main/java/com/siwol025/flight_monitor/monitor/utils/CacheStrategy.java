package com.siwol025.flight_monitor.monitor.utils;

import com.siwol025.flight_monitor.route.domain.SeatGrade;
import java.math.BigDecimal;

public interface CacheStrategy {

    BigDecimal getPreviousPrice(Long flightId, SeatGrade seatGrade, BigDecimal currentPrice);

    void saveToCache(Long flightId, SeatGrade seatGrade, BigDecimal price);
}
