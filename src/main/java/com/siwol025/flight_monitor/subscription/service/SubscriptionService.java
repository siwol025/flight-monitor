package com.siwol025.flight_monitor.subscription.service;

import com.siwol025.flight_monitor.global.exception.ErrorTag;
import com.siwol025.flight_monitor.global.exception.custom.BadRequestException;
import com.siwol025.flight_monitor.global.exception.custom.NotFoundException;
import com.siwol025.flight_monitor.route.domain.Route;
import com.siwol025.flight_monitor.route.domain.RouteKey;
import com.siwol025.flight_monitor.route.domain.RouteLatestFare;
import com.siwol025.flight_monitor.route.domain.RouteStatus;
import com.siwol025.flight_monitor.route.domain.SeatGrade;
import com.siwol025.flight_monitor.route.repository.RouteLatestFareRepository;
import com.siwol025.flight_monitor.route.repository.RouteRepository;
import com.siwol025.flight_monitor.route.service.RouteService;
import com.siwol025.flight_monitor.subscription.domain.Subscription;
import com.siwol025.flight_monitor.subscription.domain.SubscriptionStatus;
import com.siwol025.flight_monitor.subscription.dto.SubscriberWithConditionDto;
import com.siwol025.flight_monitor.subscription.dto.response.SubscriptionDetailResponse;
import com.siwol025.flight_monitor.subscription.dto.response.SubscriptionResponse;
import com.siwol025.flight_monitor.subscription.repository.SubscriptionRepository;
import com.siwol025.flight_monitor.user.domain.User;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final RouteService routeService;
    private final RouteRepository routeRepository;
    private final RouteLatestFareRepository routeLatestFareRepository;
    private final Clock clock;

    @Transactional
    public void subscribe(User user, String departureAirport, String arrivalAirport, LocalDate departureDate,
                          SeatGrade seatGrade, BigDecimal targetPrice, BigDecimal dropThresholdPercent) {
        RouteKey key = RouteKey.of(departureAirport, arrivalAirport, departureDate, seatGrade, clock);
        // findOrCreate가 돌려주는 노선은 이 트랜잭션 입장에서 분리 상태라 id로만 참조한다
        Route route = routeService.findOrCreate(key);
        // 무효 노선은 감시 대상이 아니라 구독해도 가격이 영원히 채워지지 않는다
        if (route.getStatus() == RouteStatus.INVALID) {
            throw new BadRequestException(ErrorTag.INVALID_ROUTE_SUBSCRIPTION);
        }
        Long routeId = route.getId();
        validateDuplicateSubscription(user.getId(), routeId);

        // 외부 호출 없이 저장된 최신 최저가를 기준가로 쓴다. 처음 보는 노선은 측정 대기(null)로 두고 감시가 채운다
        BigDecimal baselinePrice = routeLatestFareRepository.findByRouteId(routeId)
                .map(RouteLatestFare::getAmount)
                .orElse(null);

        Subscription subscription = Subscription.builder()
                .user(user)
                .route(routeRepository.getReferenceById(routeId))
                .price(baselinePrice)
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
        Map<Long, RouteLatestFare> latestFares = findLatestFares(subscriptions);

        return subscriptions.stream()
                .map(subscription -> SubscriptionResponse.of(subscription, latestFares.get(subscription.getRoute().getId())))
                .toList();
    }

    private Map<Long, RouteLatestFare> findLatestFares(List<Subscription> subscriptions) {
        List<Long> routeIds = subscriptions.stream()
                .map(subscription -> subscription.getRoute().getId())
                .toList();
        return routeLatestFareRepository.findAllByRouteIdIn(routeIds).stream()
                .collect(Collectors.toMap(latestFare -> latestFare.getRoute().getId(), Function.identity()));
    }

    public List<SubscriberWithConditionDto> getSubscribersWithCondition(Long routeId) {
        return subscriptionRepository.findSubscribersWithCondition(routeId, SubscriptionStatus.ACTIVE);
    }

    public SubscriptionDetailResponse getSubscription(User user, Long subscriptionId) {
        Subscription subscription = findSubscription(subscriptionId);
        subscription.validateOwner(user);

        RouteLatestFare latestFare = routeLatestFareRepository.findByRouteId(subscription.getRoute().getId())
                .orElse(null);

        return SubscriptionDetailResponse.of(subscription, latestFare);
    }

    /**
     * 노선의 측정 대기 구독(기준가 비어 있음)에 기준가를 채운다. 운임이 있는 모든 관측에서 부른다.
     * 첫 관측에서만 채우면, 등록이 "최신가 없음"을 읽고 아직 커밋하지 않은 사이에 첫 관측이 끝난 구독이 영구히 측정 대기로 남는다.
     */
    @Transactional
    public void fillPendingBaselinePrice(Long routeId, BigDecimal price) {
        subscriptionRepository.fillPendingBaselinePrice(routeId, price);
    }

    /**
     * 감시할 노선 ID(활성 구독이 있고 출발일이 지나지 않은 활성 노선, 중복 없음). 출발일 당일 노선은 그날이 끝날 때까지 감시한다.
     */
    public List<Long> getActiveRouteIds() {
        return subscriptionRepository.findActiveRouteIds(SubscriptionStatus.ACTIVE, RouteStatus.ACTIVE, LocalDate.now(clock));
    }

    private void validateDuplicateSubscription(Long userId, Long routeId) {
        if (subscriptionRepository.existsByUserIdAndRouteId(userId, routeId)) {
            throw new BadRequestException(ErrorTag.DUPLICATE_SUBSCRIPTION);
        }
    }

    private Subscription findSubscription(Long subscriptionId) {
        return subscriptionRepository.findByIdWithRoute(subscriptionId)
                .orElseThrow(() -> new NotFoundException(ErrorTag.SUBSCRIPTION_NOT_FOUND));
    }
}
