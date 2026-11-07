package com.siwol025.flight_monitor.fare;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 업체 주문의 현재 상태. 업체가 주지 않은 값은 null·빈 목록이다.
 *
 * @param bookingReference  항공사 예약 번호(PNR)
 * @param amount            주문 총액. 업체 표기 자릿수 그대로(결제 금액으로 그대로 보낸다)
 * @param paymentRequiredBy 이 시각까지 결제하지 않으면 좌석이 풀린다(hold 미결제일 때만)
 * @param paidAt            결제된 시각. null이면 미결제
 * @param ticketNumbers     발권된 항공권 번호. 비어 있으면 미발권
 * @param bookingId         주문에 남긴 우리 예매 ID
 */
public record FlightOrder(
        String orderId,
        String offerId,
        String bookingReference,
        BigDecimal amount,
        String currency,
        Instant paymentRequiredBy,
        Instant paidAt,
        List<String> ticketNumbers,
        String bookingId
) {
}
