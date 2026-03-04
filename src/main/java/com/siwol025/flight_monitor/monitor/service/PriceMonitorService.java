package com.siwol025.flight_monitor.monitor.service;

import com.siwol025.flight_monitor.global.annotation.DistributedLock;
import com.siwol025.flight_monitor.mock.flight.dto.response.MockFlightResponse;
import com.siwol025.flight_monitor.monitor.utils.ApiCooldownCircuitBreaker;
import com.siwol025.flight_monitor.monitor.utils.DynamicTtlCalculator;
import com.siwol025.flight_monitor.subscription.domain.flight.Flight;
import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import com.siwol025.flight_monitor.subscription.service.FlightFetcher;
import com.siwol025.flight_monitor.subscription.service.FlightService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile("dev")
@RequiredArgsConstructor
public class PriceMonitorService {

    private final StringRedisTemplate redisTemplate;
    private final FlightFetcher flightFetcher;
    private final FlightService flightService;
    private final FlightSeatGradePriceService flightSeatGradePriceService;
    private final DynamicTtlCalculator ttlCalculator;
    private final ApiCooldownCircuitBreaker cooldownCircuitBreaker;

    private static final String API_COOLDOWN_PREFIX = "flight:api_cooldown:";
    private static final String FLIGHT_PRICE_PREFIX = "flight:price:";

    @DistributedLock(key = "#flightId")
    public void checkAndUpdatePrice(Long flightId, SeatGrade seatGrade) {
        if (hasCoolDownKey(flightId)) {
            return;
        }

        MockFlightResponse latestInfo = flightFetcher.fetchMockFlight(flightId);
        BigDecimal currentPrice = latestInfo.getPriceBySeatClass(seatGrade);

        Flight flight = flightService.getFlight(flightId);
        setCoolDownKey(flightId, flight.getDepartureTime());

        String redisKey = FLIGHT_PRICE_PREFIX + flightId + ":" + seatGrade.name();
        if (isPriceUnchanged(redisKey, currentPrice)) {
            return;
        }

        flightSeatGradePriceService.processPublishAndPriceUpdate(flight, seatGrade, currentPrice, latestInfo, redisKey);
    }

    private boolean hasCoolDownKey(Long flightId) {
        String cooldownKey = API_COOLDOWN_PREFIX + flightId;

        return cooldownCircuitBreaker.hasCooldown(cooldownKey);
    }

    private void setCoolDownKey(Long flightId, LocalDateTime departureTime) {
        String cooldownKey = API_COOLDOWN_PREFIX + flightId;
        Duration nextTtl = ttlCalculator.calculateCooldownTtl(departureTime, LocalDateTime.now());

        if (!nextTtl.isZero()) {
            cooldownCircuitBreaker.setCooldown(cooldownKey, nextTtl);
        }
    }

    private boolean isPriceUnchanged(String redisKey, BigDecimal currentPrice) {
        String previousPrice = redisTemplate.opsForValue().get(redisKey);

        return previousPrice != null && new BigDecimal(previousPrice).compareTo(currentPrice) == 0;
    }
}