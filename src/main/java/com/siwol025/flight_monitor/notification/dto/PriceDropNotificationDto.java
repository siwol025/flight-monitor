package com.siwol025.flight_monitor.notification.dto;

import com.siwol025.flight_monitor.route.domain.SeatGrade;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;

/**
 * 노선 최저가 하락 이벤트. 메일 문구를 이벤트만으로 만들 수 있도록 노선 4필드와 그때 최저가였던 항공편 정보를 싣는다.
 */
@Builder
public record PriceDropNotificationDto(
        Long routeId,
        String departureAirport,
        String arrivalAirport,
        LocalDate departureDate,
        SeatGrade seatGrade,
        BigDecimal oldPrice,
        BigDecimal newPrice,
        String currency,
        String carrierCode,
        String flightNumber,        // 업체가 준 숫자 편명(항공사 코드와 따로)
        LocalDateTime departingAt,  // 업체가 준 현지 시각
        LocalDateTime arrivingAt,
        LocalDateTime detectedAt
) {
}
