package com.siwol025.flight_monitor.subscription.dto.response;

import com.siwol025.flight_monitor.route.domain.Route;
import com.siwol025.flight_monitor.route.domain.RouteLatestFare;
import com.siwol025.flight_monitor.subscription.domain.Subscription;
import com.siwol025.flight_monitor.subscription.domain.SubscriptionStatus;
import com.siwol025.flight_monitor.route.domain.SeatGrade;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;

@Builder
public record SubscriptionResponse(
        Long subscriptionId,
        String departureAirportCode,
        String arrivalAirportCode,
        LocalDate departureDate,
        SeatGrade seatGrade,
        BigDecimal subscribedPrice, // 구독 시점의 가격 (기준점). 측정 대기 동안 null
        BigDecimal latestPrice,     // 저장된 노선 최신 최저가
        String currency,
        FareStatus fareStatus,
        BigDecimal targetPrice,
        BigDecimal dropThresholdPercent,
        LocalDateTime createdAt,
        SubscriptionStatus status
) {
    /**
     * @param latestFare 노선의 최신 최저가. 아직 관측 전이면 null
     */
    public static SubscriptionResponse of(Subscription subscription, RouteLatestFare latestFare) {
        Route route = subscription.getRoute();
        return SubscriptionResponse.builder()
                .subscriptionId(subscription.getId())
                .departureAirportCode(route.getDepartureAirport())
                .arrivalAirportCode(route.getArrivalAirport())
                .departureDate(route.getDepartureDate())
                .seatGrade(route.getSeatGrade())
                .subscribedPrice(subscription.getPrice())
                .latestPrice(latestFare == null ? null : latestFare.getAmount())
                .currency(latestFare == null ? null : latestFare.getCurrency())
                .fareStatus(FareStatus.of(route, latestFare))
                .targetPrice(subscription.getTargetPrice())
                .dropThresholdPercent(subscription.getDropThresholdPercent())
                .createdAt(subscription.getCreatedAt())
                .status(subscription.getStatus())
                .build();
    }
}
