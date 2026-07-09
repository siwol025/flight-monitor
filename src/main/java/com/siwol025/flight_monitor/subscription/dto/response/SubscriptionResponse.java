package com.siwol025.flight_monitor.subscription.dto.response;

import com.siwol025.flight_monitor.subscription.domain.Subscription;
import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;

@Builder
public record SubscriptionResponse(
        Long subscriptionId,
        String flightNumber,
        String airlineCode,
        String departureAirportCode,
        String arrivalAirportCode,
        LocalDateTime departureTime,
        SeatGrade seatGrade,
        BigDecimal subscribedPrice, // 구독 시점의 가격 (기준점)
        BigDecimal targetPrice,
        BigDecimal dropThresholdPercent,
        LocalDateTime createdAt
) {
    public static SubscriptionResponse from(Subscription subscription) {
        return SubscriptionResponse.builder()
                .subscriptionId(subscription.getId())
                .flightNumber(subscription.getFlight().getFlightNumber())
                .airlineCode(subscription.getFlight().getAirlineCode())
                .departureAirportCode(subscription.getFlight().getDepartureAirport())
                .arrivalAirportCode(subscription.getFlight().getArrivalAirport())
                .departureTime(subscription.getFlight().getDepartureTime())
                .seatGrade(subscription.getSeatGrade())
                .subscribedPrice(subscription.getPrice())
                .targetPrice(subscription.getTargetPrice())
                .dropThresholdPercent(subscription.getDropThresholdPercent())
                .createdAt(subscription.getCreatedAt())
                .build();
    }
}
