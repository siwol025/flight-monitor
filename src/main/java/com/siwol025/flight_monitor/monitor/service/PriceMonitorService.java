package com.siwol025.flight_monitor.monitor.service;

import com.siwol025.flight_monitor.global.annotation.DistributedLock;
import com.siwol025.flight_monitor.mock.flight.dto.response.MockFlightResponse;
import com.siwol025.flight_monitor.monitor.domain.FlightLatestPriceInfo;
import com.siwol025.flight_monitor.monitor.dto.PriceDropNotificationDto;
import com.siwol025.flight_monitor.monitor.producer.NotificationProducer;
import com.siwol025.flight_monitor.monitor.utils.ApiCooldownCircuitBreaker;
import com.siwol025.flight_monitor.monitor.utils.DynamicTtlCalculator;
import com.siwol025.flight_monitor.subscription.domain.flight.Flight;
import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import com.siwol025.flight_monitor.subscription.dto.FlightMonitorTaskDto;
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
    private final NotificationProducer producer;
    private final FlightLatestPriceInfoService flightLatestPriceInfoService;
    private final DynamicTtlCalculator ttlCalculator;
    private final ApiCooldownCircuitBreaker cooldownCircuitBreaker;

    private static final String API_COOLDOWN_PREFIX = "flight:api_cooldown:";
    private static final String FLIGHT_PRICE_PREFIX = "flight:price:";

    public void checkPriceAndNotify(FlightMonitorTaskDto taskDto) {
        if (hasCoolDownKey(taskDto.flightId(), taskDto.seatGrade())) {
            log.info("📥 [CoolDown] FlightID={}, SeatGrade={}", taskDto.flightId(), taskDto.seatGrade());
            return;
        }

        MockFlightResponse latestInfo = flightFetcher.fetchMockFlight(taskDto.flightId());
        if (latestInfo == null) {
            log.warn("⚠️ [Monitor] API 응답 없음: FlightID={}, SeatGrade={}", taskDto.flightId(), taskDto.seatGrade());
            return;
        }
        BigDecimal currentPrice = latestInfo.getPriceBySeatClass(taskDto.seatGrade());

        BigDecimal previousPrice = getPreviousPrice(taskDto.flightId(), taskDto.seatGrade(), currentPrice);
        setCoolDownKey(taskDto.flightId(), taskDto.seatGrade(), latestInfo.departureTime());

        if (isPriceUnchanged(previousPrice, currentPrice)) {
            return;
        }

        flightLatestPriceInfoService.updateLatestPriceInfo(taskDto.flightId(), taskDto.seatGrade(), currentPrice);

        String redisKey = FLIGHT_PRICE_PREFIX + taskDto.flightId() + ":" + taskDto.seatGrade().name();
        redisTemplate.opsForValue().set(redisKey, currentPrice.toString(), Duration.ofDays(7));

        publishNotifyQueue(previousPrice, currentPrice, latestInfo, taskDto.seatGrade());
    }

    private boolean hasCoolDownKey(Long flightId, SeatGrade seatGrade) {
        String cooldownKey = API_COOLDOWN_PREFIX + flightId + ":" + seatGrade.name();

        return cooldownCircuitBreaker.hasCooldown(cooldownKey);
    }

    private void setCoolDownKey(Long flightId, SeatGrade seatGrade, LocalDateTime departureTime) {
        String cooldownKey = API_COOLDOWN_PREFIX + flightId + ":" + seatGrade.name();
        Duration nextTtl = ttlCalculator.calculateCooldownTtl(departureTime, LocalDateTime.now());

        if (!nextTtl.isZero()) {
            cooldownCircuitBreaker.setCooldown(cooldownKey, nextTtl);
        }
    }

    private BigDecimal getPreviousPrice(Long flightId, SeatGrade seatGrade, BigDecimal currentPrice) {
        String redisKey = FLIGHT_PRICE_PREFIX + flightId + ":" + seatGrade.name();
        String cachedPriceStr = redisTemplate.opsForValue().get(redisKey);

        if (cachedPriceStr != null) {
            return new BigDecimal(cachedPriceStr);
        }

        BigDecimal dbPrice = flightLatestPriceInfoService.getPreviousPrice(flightId, seatGrade)
                .orElseGet(() -> {
                    log.info("ℹ️ [Monitor] DB에 이전 가격 정보 없음. 초기 가격을 세팅합니다.");
                    return flightLatestPriceInfoService.createLatestPriceInfo(flightId, seatGrade, currentPrice).getPrice();
                });
        redisTemplate.opsForValue().set(redisKey, dbPrice.toString(), Duration.ofDays(7));

        return dbPrice;
    }

    private void publishNotifyQueue(BigDecimal previousPrice, BigDecimal currentPrice, MockFlightResponse mockFlightResponse, SeatGrade seatGrade) {
        if (isPriceDropped(previousPrice, currentPrice)) {
            PriceDropNotificationDto priceDropNotificationDto = PriceDropNotificationDto.builder()
                    .flightId(mockFlightResponse.id())
                    .flightNumber(mockFlightResponse.flightNumber())
                    .seatGrade(seatGrade)
                    .oldPrice(previousPrice)
                    .newPrice(currentPrice)
                    .detectedAt(LocalDateTime.now())
                    .build();

            producer.publishPriceDrop(priceDropNotificationDto);
        }
    }

    private boolean isPriceUnchanged(BigDecimal previousPrice, BigDecimal currentPrice) {
        return previousPrice != null && previousPrice.compareTo(currentPrice) == 0;
    }

    private boolean isPriceDropped(BigDecimal previousPrice, BigDecimal currentPrice) {
        return  currentPrice.compareTo(previousPrice) < 0;
    }
}