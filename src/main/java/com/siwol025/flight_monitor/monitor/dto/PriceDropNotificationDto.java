package com.siwol025.flight_monitor.monitor.dto;

import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;

@Builder
public record PriceDropNotificationDto(
        Long flightId,
        String flightNumber,
        SeatGrade seatGrade,
        BigDecimal oldPrice,
        BigDecimal newPrice,
        LocalDateTime detectedAt
) {
}
