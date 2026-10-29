package com.siwol025.flight_monitor.fare;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 노선의 최저가 1건과 그 운임의 항공편 정보. 시각은 업체가 준 현지 시각 그대로다(오프셋 없음).
 */
public record LowestFare(
        BigDecimal amount,
        String currency,
        String carrierCode,
        String carrierName,
        String flightNumber,
        LocalDateTime departingAt,
        LocalDateTime arrivingAt
) {
}
