package com.siwol025.flight_monitor.fare;

import java.math.BigDecimal;

/**
 * 업체가 확정한 주문. bookingReference는 항공사 예약 번호(PNR)다.
 */
public record PlacedOrder(
        String orderId,
        String bookingReference,
        BigDecimal amount,
        String currency
) {
}
