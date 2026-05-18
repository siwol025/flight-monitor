package com.siwol025.flight_monitor.monitor.utils;

import com.siwol025.flight_monitor.monitor.service.FlightLatestPriceInfoService;
import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import java.math.BigDecimal;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

@Slf4j
@Component
@RequiredArgsConstructor
public class FlightPriceCacheManager {

    private final StringRedisTemplate redisTemplate;
    private final FlightLatestPriceInfoService flightLatestPriceInfoService;

    private static final String FLIGHT_PRICE_PREFIX = "flight:price:";

    @CircuitBreaker(name = "redisPopCircuitBreaker", fallbackMethod = "dbFallbackPreviousPrice")
    public BigDecimal getPreviousPrice(Long flightId, SeatGrade seatGrade, BigDecimal currentPrice) {
        String redisKey = FLIGHT_PRICE_PREFIX + flightId + ":" + seatGrade.name();
        String cachedPriceStr = redisTemplate.opsForValue().get(redisKey);

        if (cachedPriceStr != null) {
            return new BigDecimal(cachedPriceStr);
        }

        return flightLatestPriceInfoService.getPreviousPrice(flightId, seatGrade)
                .orElseGet(() -> {
                    log.info("ℹ️ [Monitor] DB에 이전 가격 정보 없음. 초기 가격을 세팅합니다.");
                    return flightLatestPriceInfoService.createLatestPriceInfo(flightId, seatGrade, currentPrice).getPrice();
                });
    }

    public BigDecimal dbFallbackPreviousPrice(Long flightId, SeatGrade seatGrade, BigDecimal currentPrice, Throwable t) {
        log.warn("🚨 [CircuitBreaker] Redis 장애. DB에서 가격을 조회합니다.");

        return flightLatestPriceInfoService.getPreviousPrice(flightId, seatGrade)
                .orElseGet(() -> flightLatestPriceInfoService.createLatestPriceInfo(flightId, seatGrade, currentPrice).getPrice());
    }

    @CircuitBreaker(name = "redisPopCircuitBreaker", fallbackMethod = "dbFallbackSaveToCache")
    public void saveToCache(String redisKey, String value) {
        redisTemplate.opsForValue().set(redisKey, value, Duration.ofDays(1));
    }

    public void dbFallbackSaveToCache(String redisKey, String value, Throwable t) {
        log.warn("🚨 [CircuitBreaker] Redis가 다운되어 캐시 갱신을 포기합니다. (Key: {})", redisKey);
    }
}
