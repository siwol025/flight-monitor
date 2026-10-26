package com.siwol025.flight_monitor.notification.dto;

import com.siwol025.flight_monitor.route.domain.SeatGrade;
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
