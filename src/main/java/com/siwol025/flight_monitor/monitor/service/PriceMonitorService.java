package com.siwol025.flight_monitor.monitor.service;

import com.siwol025.flight_monitor.fare.FareProviderConfigurationException;
import com.siwol025.flight_monitor.fare.FareProviderException;
import com.siwol025.flight_monitor.fare.FareRateLimitedException;
import com.siwol025.flight_monitor.fare.InvalidRouteException;
import com.siwol025.flight_monitor.fare.LowestFare;
import com.siwol025.flight_monitor.fare.LowestFareFinder;
import com.siwol025.flight_monitor.monitor.metrics.PipelineMetrics;
import com.siwol025.flight_monitor.monitor.worker.RedisRateLimiter;
import com.siwol025.flight_monitor.notification.dto.PriceDropNotificationDto;
import com.siwol025.flight_monitor.notification.producer.NotificationPublisher;
import com.siwol025.flight_monitor.route.domain.Route;
import com.siwol025.flight_monitor.route.domain.RouteStatus;
import com.siwol025.flight_monitor.route.repository.RouteRepository;
import com.siwol025.flight_monitor.route.service.RouteService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@Profile("!mock")
public class PriceMonitorService {

    private final RouteRepository routeRepository;
    private final RouteService routeService;
    private final LowestFareFinder lowestFareFinder;
    private final RouteLatestFareService routeLatestFareService;
    private final NotificationPublisher producer;
    private final PipelineMetrics pipelineMetrics;
    private final Clock clock;
    // 공유 레이트 리미터. monitor.ratelimiter.enabled=false면 없다
    private final ObjectProvider<RedisRateLimiter> rateLimiter;

    public void checkPriceAndNotify(Long routeId) {
        // 큐에 남아 있던 무효(또는 사라진) 노선 작업이 업체 호출 예산을 쓰지 않게 한다
        Route route = routeRepository.findById(routeId).orElse(null);
        if (route == null || route.getStatus() == RouteStatus.INVALID) {
            log.info("[PriceMonitorService] 감시 대상이 아닌 노선이라 건너뜁니다: routeId={}", routeId);
            return;
        }

        Optional<LowestFare> lowestFare;
        long startNanos = System.nanoTime();
        try {
            lowestFare = lowestFareFinder.findLowestFare(route.toKey());
        } catch (InvalidRouteException e) {
            log.warn("[PriceMonitorService] 업체가 노선을 거부해 무효 처리합니다: routeId={}, 원인={}", routeId, e.getMessage());
            routeService.markInvalid(routeId);
            return;
        } catch (FareRateLimitedException e) {
            // 업체 한도는 API 키 단위라 재개 시각까지 모든 인스턴스의 호출을 멈춘다. 이번 노선은 저장하지 않는다
            log.warn("[PriceMonitorService] 운임 업체 호출 한도 초과, {}까지 전체 정지합니다: routeId={}", e.getResetAt(), routeId);
            rateLimiter.ifAvailable(limiter -> limiter.pauseUntil(e.getResetAt()));
            return;
        } catch (FareProviderConfigurationException e) {
            log.error("[PriceMonitorService] 운임 업체 설정 오류(인증 등), 저장하지 않습니다: routeId={}", routeId, e);
            return;
        } catch (FareProviderException e) {
            // 업체 장애·호출 한도 초과는 저장하지 않고 다음 주기에 다시 확인한다
            log.warn("[PriceMonitorService] 운임 업체 오류, 저장하지 않습니다: routeId={}, 원인={}", routeId, e.getMessage());
            return;
        } finally {
            // max-in-flight 산정식(p99)의 출처라 실패한 호출도 기록한다. 어댑터는 지표에 의존하지 않는다
            pipelineMetrics.recordExternalApiLatency(Duration.ofNanos(System.nanoTime() - startNanos));
        }
        if (lowestFare.isEmpty()) {
            log.info("[PriceMonitorService] 운임 없음, 마지막 최저가를 유지합니다: routeId={}", routeId);
            return;
        }

        // 최신 최저가 갱신 트랜잭션이 커밋된 뒤에 발행한다
        routeLatestFareService.record(routeId, lowestFare.get())
                .ifPresent(oldPrice -> publishPriceDrop(route, oldPrice, lowestFare.get()));
    }

    private void publishPriceDrop(Route route, BigDecimal oldPrice, LowestFare fare) {
        producer.publishPriceDrop(PriceDropNotificationDto.builder()
                .routeId(route.getId())
                .departureAirport(route.getDepartureAirport())
                .arrivalAirport(route.getArrivalAirport())
                .departureDate(route.getDepartureDate())
                .seatGrade(route.getSeatGrade())
                .oldPrice(oldPrice)
                .newPrice(fare.amount())
                .currency(fare.currency())
                .carrierCode(fare.carrierCode())
                .flightNumber(fare.flightNumber())
                .departingAt(fare.departingAt())
                .arrivingAt(fare.arrivingAt())
                .detectedAt(LocalDateTime.now(clock))
                .build());
    }
}
