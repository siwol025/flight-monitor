package com.siwol025.flight_monitor.subscription.service;

import com.siwol025.flight_monitor.mock.flight.dto.response.MockFlightResponse;
import com.siwol025.flight_monitor.mock.flight.dto.response.MockFlightSeatPriceResponse;
import com.siwol025.flight_monitor.subscription.domain.Subscription;
import com.siwol025.flight_monitor.subscription.domain.SubscriptionStatus;
import com.siwol025.flight_monitor.subscription.domain.flight.Flight;
import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import com.siwol025.flight_monitor.subscription.dto.FlightSeatGradeDto;
import com.siwol025.flight_monitor.subscription.dto.response.SubscriptionDetailResponse;
import com.siwol025.flight_monitor.subscription.dto.response.SubscriptionResponse;
import com.siwol025.flight_monitor.subscription.repository.SubscriptionRepository;
import com.siwol025.flight_monitor.user.domain.User;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final FlightService flightService;
    private final FlightFetcher flightFetcher;

    @Transactional
    public void subscribe(User user, Long flightId, SeatGrade seatGrade) {
        validateDuplicateSubscription(user.getId(), flightId, seatGrade);

        MockFlightResponse response = flightFetcher.fetchMockFlight(flightId);
        Flight flight = flightService.findOrCreateFlight(response);
        BigDecimal currentPrice = extractPriceByGrade(response, seatGrade);

        Subscription subscription = Subscription.builder()
                .user(user)
                .flight(flight)
                .seatGrade(seatGrade)
                .price(currentPrice)
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

    public SubscriptionDetailResponse getSubscription(Long subscriptionId) {
        Subscription subscription = findSubscription(subscriptionId);

        MockFlightResponse mockFlightResponse = flightFetcher.fetchMockFlight(subscription.getFlight().getId());

        BigDecimal currentPrice = mockFlightResponse.seatPrices().stream()
                .filter(sp -> sp.seatGrade() == subscription.getSeatGrade())
                .map(MockFlightSeatPriceResponse::price)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("현재 해당 좌석의 가격 정보를 가져올 수 없습니다."));

        return SubscriptionDetailResponse.of(subscription, currentPrice);
    }

    private BigDecimal extractPriceByGrade(MockFlightResponse response, SeatGrade seatGrade) {
        return response.seatPrices().stream()
                .filter(sp -> sp.seatGrade() == seatGrade)
                .map(MockFlightSeatPriceResponse::price)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("해당 좌석 등급의 가격 정보를 찾을 수 없습니다."));
    }

    private void validateDuplicateSubscription(Long userId, Long flightId, SeatGrade seatGrade) {
        if (subscriptionRepository.existsByUserIdAndFlightIdAndSeatGrade(userId, flightId, seatGrade)) {
            throw new IllegalArgumentException("이미 등록된 구독입니다.");
        }
    }

    private Subscription findSubscription(Long subscriptionId) {
        return subscriptionRepository.findByIdWithFlight(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("구독내역을 찾을 수 없습니다."));
    }

    public List<FlightSeatGradeDto> getActiveFlights() {
        return subscriptionRepository.findActiveFlightIdsAndSeatGrade(SubscriptionStatus.ACTIVE);
    }
}
