package com.siwol025.flight_monitor.monitor.service;

import com.siwol025.flight_monitor.fare.LowestFare;
import com.siwol025.flight_monitor.route.domain.RouteLatestFare;
import com.siwol025.flight_monitor.route.repository.RouteLatestFareRepository;
import com.siwol025.flight_monitor.route.repository.RouteRepository;
import com.siwol025.flight_monitor.subscription.service.SubscriptionService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 노선별 최신 최저가(이전 가격의 단일 저장소)를 관측 결과로 저장·갱신한다.
 */
@Service
@RequiredArgsConstructor
public class RouteLatestFareService {

    private final RouteLatestFareRepository routeLatestFareRepository;
    private final RouteRepository routeRepository;
    private final SubscriptionService subscriptionService;
    private final Clock clock;

    /**
     * @return 가격 하락 여부와 직전 관측부터의 간격. 호출자는 이 트랜잭션이 커밋된 뒤 하락 이벤트를 발행하고 간격을 기록한다
     */
    @Transactional
    public FareObservationResult record(Long routeId, LowestFare fare) {
        Instant observedAt = clock.instant();
        Optional<BigDecimal> droppedFrom = Optional.empty();
        Optional<Duration> refreshInterval = Optional.empty();

        Optional<RouteLatestFare> found = routeLatestFareRepository.findByRouteId(routeId);
        if (found.isEmpty()) {
            routeLatestFareRepository.save(RouteLatestFare.builder()
                    .route(routeRepository.getReferenceById(routeId))
                    .amount(fare.amount())
                    .currency(fare.currency())
                    .carrierCode(fare.carrierCode())
                    .carrierName(fare.carrierName())
                    .flightNumber(fare.flightNumber())
                    .departingAt(fare.departingAt())
                    .arrivingAt(fare.arrivingAt())
                    .observedAt(observedAt)
                    .build());
        } else {
            RouteLatestFare latest = found.get();
            refreshInterval = Optional.of(Duration.between(latest.getObservedAt(), observedAt));
            if (!latest.getCurrency().equals(fare.currency())) {
                // 통화가 다르면 금액을 비교할 수 없다. 새 통화의 값을 다음 비교의 기준으로 삼는다(알림 없음)
                update(latest, fare, observedAt);
            } else {
                // 금액은 compareTo로 비교한다(45.00과 45는 같은 가격)
                int comparison = fare.amount().compareTo(latest.getAmount());
                if (comparison == 0) {
                    latest.markObserved(observedAt);
                } else {
                    if (comparison < 0) {
                        droppedFrom = Optional.of(latest.getAmount());
                    }
                    update(latest, fare, observedAt);
                }
            }
        }

        // 기준가 채우기는 실행 전 flush·실행 후 clear하므로 엔티티 변경 뒤에 부른다
        subscriptionService.fillPendingBaselinePrice(routeId, fare.amount());
        return new FareObservationResult(droppedFrom, refreshInterval);
    }

    private void update(RouteLatestFare latest, LowestFare fare, Instant observedAt) {
        latest.update(fare.amount(), fare.currency(), fare.carrierCode(), fare.carrierName(), fare.flightNumber(),
                fare.departingAt(), fare.arrivingAt(), observedAt);
    }
}
