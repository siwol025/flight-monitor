package com.siwol025.flight_monitor.monitor.service;

import com.siwol025.flight_monitor.mock.flight.dto.response.MockFlightResponse;
import com.siwol025.flight_monitor.monitor.dto.PriceDropNotificationDto;
import com.siwol025.flight_monitor.monitor.producer.NotificationPublisher;
import com.siwol025.flight_monitor.monitor.utils.FlightPriceCacheManager;
import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import com.siwol025.flight_monitor.subscription.dto.FlightMonitorTaskDto;
import com.siwol025.flight_monitor.subscription.service.FlightFetcher;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceMonitorService {

    private final FlightFetcher flightFetcher;
    private final NotificationPublisher producer;
    private final FlightLatestPriceInfoService flightLatestPriceInfoService;
    private final FlightPriceCacheManager cacheManager;

    private static final String FLIGHT_PRICE_PREFIX = "flight:price:";

    public void checkPriceAndNotify(FlightMonitorTaskDto taskDto) {
        MockFlightResponse latestInfo = flightFetcher.fetchMockFlight(taskDto.flightId());
        if (latestInfo == null) {
            log.warn("⚠️ [Monitor] API 응답 없음: FlightID={}, SeatGrade={}", taskDto.flightId(), taskDto.seatGrade());
            return;
        }
        BigDecimal currentPrice = latestInfo.getPriceBySeatClass(taskDto.seatGrade());

        BigDecimal cachedPreviousPrice = cacheManager.getPreviousPrice(taskDto.flightId(), taskDto.seatGrade(), currentPrice);

        if (isPriceUnchanged(cachedPreviousPrice, currentPrice)) {
            return;
        }

        BigDecimal dbPreviousPrice = flightLatestPriceInfoService.getPreviousPrice(taskDto.flightId(), taskDto.seatGrade())
                        .orElse(currentPrice);

        if (isPriceUnchanged(dbPreviousPrice, currentPrice)) {
            log.info("🔄 [Self-Healing] 오래된 캐시(Stale Cache) 감지됨. 알림은 생략하고 캐시만 최신화합니다: {}", taskDto.flightId());
            String redisKey = FLIGHT_PRICE_PREFIX + taskDto.flightId() + ":" + taskDto.seatGrade().name();
            cacheManager.saveToCache(redisKey, currentPrice.toString());
            return;
        }

        flightLatestPriceInfoService.updateLatestPriceInfo(taskDto.flightId(), taskDto.seatGrade(), currentPrice);

        String redisKey = FLIGHT_PRICE_PREFIX + taskDto.flightId() + ":" + taskDto.seatGrade().name();
        cacheManager.saveToCache(redisKey, currentPrice.toString());

        publishNotifyQueue(dbPreviousPrice, currentPrice, latestInfo, taskDto.seatGrade());
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