package com.siwol025.flight_monitor.subscription.dto.response;

import com.siwol025.flight_monitor.route.domain.Route;
import com.siwol025.flight_monitor.route.domain.RouteLatestFare;
import com.siwol025.flight_monitor.subscription.domain.Subscription;
import com.siwol025.flight_monitor.subscription.domain.SubscriptionStatus;
import com.siwol025.flight_monitor.route.domain.SeatGrade;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;

@Builder
public record SubscriptionDetailResponse(
        Long subscriptionId,
        String departureAirportCode,
        String arrivalAirportCode,
        LocalDate departureDate,
        SeatGrade seatGrade,
        BigDecimal subscribedPrice, // 구독 시점 가격. 측정 대기 동안 null
        BigDecimal latestPrice,     // 저장된 노선 최신 최저가
        String currency,
        String carrierCode,         // 최신 최저가 항공편 정보(편명은 항공사 코드와 따로 준다)
        String carrierName,
        String flightNumber,
        LocalDateTime departingAt,  // 업체가 준 현지 시각
        LocalDateTime arrivingAt,
        Instant observedAt,
        BigDecimal priceDifference, // 변동 금액 (latestPrice - subscribedPrice). 둘 중 하나라도 없으면 null
        FareStatus fareStatus,
        BigDecimal targetPrice,
        BigDecimal dropThresholdPercent,
        LocalDateTime createdAt,
        SubscriptionStatus status
) {
    /**
     * @param latestFare 노선의 최신 최저가. 아직 관측 전이면 null
     */
    public static SubscriptionDetailResponse of(Subscription subscription, RouteLatestFare latestFare) {
        Route route = subscription.getRoute();
        SubscriptionDetailResponseBuilder builder = SubscriptionDetailResponse.builder()
                .subscriptionId(subscription.getId())
                .departureAirportCode(route.getDepartureAirport())
                .arrivalAirportCode(route.getArrivalAirport())
                .departureDate(route.getDepartureDate())
                .seatGrade(route.getSeatGrade())
                .subscribedPrice(subscription.getPrice())
                .fareStatus(FareStatus.of(route, latestFare))
                .targetPrice(subscription.getTargetPrice())
                .dropThresholdPercent(subscription.getDropThresholdPercent())
                .createdAt(subscription.getCreatedAt())
                .status(subscription.getStatus());
        if (latestFare == null) {
            return builder.build();
        }
        return builder
                .latestPrice(latestFare.getAmount())
                .currency(latestFare.getCurrency())
                .carrierCode(latestFare.getCarrierCode())
                .carrierName(latestFare.getCarrierName())
                .flightNumber(latestFare.getFlightNumber())
                .departingAt(latestFare.getDepartingAt())
                .arrivingAt(latestFare.getArrivingAt())
                .observedAt(latestFare.getObservedAt())
                .priceDifference(subscription.getPrice() == null
                        ? null
                        : latestFare.getAmount().subtract(subscription.getPrice()))
                .build();
    }
}
