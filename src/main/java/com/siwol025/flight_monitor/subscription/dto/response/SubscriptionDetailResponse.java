package com.siwol025.flight_monitor.subscription.dto.response;

import com.siwol025.flight_monitor.subscription.domain.Subscription;
import com.siwol025.flight_monitor.subscription.domain.SubscriptionStatus;
import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;

@Builder
public record SubscriptionDetailResponse(
        Long subscriptionId,
        String flightNumber,
        String airlineCode,
        String departureAirportCode,
        String arrivalAirportCode,
        LocalDateTime departureTime,
        LocalDateTime arrivalTime,
        SeatGrade seatGrade,
        BigDecimal subscribedPrice, // 구독 시점 가격
        BigDecimal currentPrice,    // 현재 조회된 최신 가격
        BigDecimal priceDifference, // 변동 금액 (currentPrice - subscribedPrice)
        BigDecimal targetPrice,
        BigDecimal dropThresholdPercent,
        LocalDateTime createdAt,
        SubscriptionStatus status
) {
    public static SubscriptionDetailResponse of(Subscription subscription, BigDecimal currentPrice) {
        return SubscriptionDetailResponse.builder()
                .subscriptionId(subscription.getId())
                .flightNumber(subscription.getFlight().getFlightNumber())
                .airlineCode(subscription.getFlight().getAirlineCode())
                .departureAirportCode(subscription.getFlight().getDepartureAirport())
                .arrivalAirportCode(subscription.getFlight().getArrivalAirport())
                .departureTime(subscription.getFlight().getDepartureTime())
                .arrivalTime(subscription.getFlight().getArrivalTime())
                .seatGrade(subscription.getSeatGrade())
                .subscribedPrice(subscription.getPrice())
                .currentPrice(currentPrice)
                .priceDifference(currentPrice.subtract(subscription.getPrice()))
                .targetPrice(subscription.getTargetPrice())
                .dropThresholdPercent(subscription.getDropThresholdPercent())
                .createdAt(subscription.getCreatedAt())
                .status(subscription.getStatus())
                .build();
    }
}
