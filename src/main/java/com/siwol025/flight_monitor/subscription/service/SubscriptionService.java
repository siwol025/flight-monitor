package com.siwol025.flight_monitor.subscription.service;

import com.siwol025.flight_monitor.global.config.CacheConfig;
import com.siwol025.flight_monitor.global.exception.ErrorTag;
import com.siwol025.flight_monitor.global.exception.custom.BadRequestException;
import com.siwol025.flight_monitor.global.exception.custom.NotFoundException;
import com.siwol025.flight_monitor.mock.flight.dto.response.MockFlightResponse;
import com.siwol025.flight_monitor.mock.flight.dto.response.MockFlightSeatPriceResponse;
import com.siwol025.flight_monitor.subscription.domain.Subscription;
import com.siwol025.flight_monitor.subscription.domain.SubscriptionStatus;
import com.siwol025.flight_monitor.subscription.domain.flight.Flight;
import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import com.siwol025.flight_monitor.subscription.dto.FlightMonitorTaskDto;
import com.siwol025.flight_monitor.subscription.dto.response.SubscriptionDetailResponse;
import com.siwol025.flight_monitor.subscription.dto.response.SubscriptionResponse;
import com.siwol025.flight_monitor.subscription.repository.SubscriptionRepository;
import com.siwol025.flight_monitor.user.domain.User;
import com.siwol025.flight_monitor.user.dto.UserEmailDto;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final FlightService flightService;
    private final FlightDataProvider flightFetcher;

    @Transactional
    public void subscribe(User user, Long flightId, SeatGrade seatGrade, BigDecimal targetPrice, BigDecimal dropThresholdPercent) {
        validateDuplicateSubscription(user.getId(), flightId, seatGrade);

        MockFlightResponse response = flightFetcher.fetchMockFlight(flightId);
        Flight flight = flightService.findOrCreateFlight(response);
        BigDecimal currentPrice = extractPriceByGrade(response, seatGrade);

        Subscription subscription = Subscription.builder()
                .user(user)
                .flight(flight)
                .seatGrade(seatGrade)
                .price(currentPrice)
                .targetPrice(targetPrice)
                .dropThresholdPercent(dropThresholdPercent)
                .build();

        subscriptionRepository.save(subscription);
    }

    @Transactional
    public void unsubscribe(User user, Long subscriptionId) {
        Subscription subscription = findSubscription(subscriptionId);
        subscription.validateOwner(user);

        subscriptionRepository.delete(subscription);
    }

    public List<SubscriptionResponse> getSubscriptions(User user) {
        List<Subscription> subscriptions = subscriptionRepository.findAllByUserId(user.getId());

        return subscriptions.stream()
                .map(SubscriptionResponse::from)
                .toList();
    }

    public List<UserEmailDto> getSubscribers(Long flightId) {
        return subscriptionRepository.findSubscriberByFlightId(flightId);
    }

    public SubscriptionDetailResponse getSubscription(User user, Long subscriptionId) {
        Subscription subscription = findSubscription(subscriptionId);
        subscription.validateOwner(user);

        MockFlightResponse mockFlightResponse = flightFetcher.fetchMockFlight(subscription.getFlight().getId());

        BigDecimal currentPrice = extractPriceByGrade(mockFlightResponse, subscription.getSeatGrade());

        return SubscriptionDetailResponse.of(subscription, currentPrice);
    }

    private BigDecimal extractPriceByGrade(MockFlightResponse response, SeatGrade seatGrade) {
        return response.seatPrices().stream()
                .filter(sp -> sp.seatGrade() == seatGrade)
                .map(MockFlightSeatPriceResponse::price)
                .findFirst()
                .orElseThrow(() -> new NotFoundException(ErrorTag.SEAT_PRICE_NOT_FOUND));
    }

    private void validateDuplicateSubscription(Long userId, Long flightId, SeatGrade seatGrade) {
        if (subscriptionRepository.existsByUserIdAndFlightIdAndSeatGrade(userId, flightId, seatGrade)) {
            throw new BadRequestException(ErrorTag.DUPLICATE_SUBSCRIPTION);
        }
    }

    private Subscription findSubscription(Long subscriptionId) {
        return subscriptionRepository.findByIdWithFlight(subscriptionId)
                .orElseThrow(() -> new NotFoundException(ErrorTag.SUBSCRIPTION_NOT_FOUND));
    }

    @Cacheable(value = CacheConfig.MONITORING_LIST_CACHE, key = "'all'")
    public List<FlightMonitorTaskDto> getActiveFlights() {
        return subscriptionRepository.findActiveFlightIdsAndSeatGrade(SubscriptionStatus.ACTIVE);
    }
}
