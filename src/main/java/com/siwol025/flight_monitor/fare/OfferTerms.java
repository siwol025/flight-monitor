package com.siwol025.flight_monitor.fare;

import java.time.Instant;
import java.util.List;

/**
 * offer를 주문할 때 지켜야 하는 업체 조건. 업체가 주지 않은 값은 빈 목록·false·null이다.
 *
 * @param passengerIds           주문 요청에 그대로 넣어야 하는 업체 승객 ID(offer의 승객 순서)
 * @param requiresInstantPayment true면 결제를 미룬 주문(hold)을 받지 않는다
 * @param expiresAt              offer 만료 시각
 * @param ownerCode              항공권을 판매하는 항공사 IATA 코드
 */
public record OfferTerms(
        List<String> passengerIds,
        boolean requiresInstantPayment,
        Instant expiresAt,
        String ownerCode
) {
}
