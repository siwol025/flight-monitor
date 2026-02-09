package com.siwol025.flight_monitor.monitor.service;

import com.siwol025.flight_monitor.mock.flight.dto.response.MockFlightResponse;
import com.siwol025.flight_monitor.monitor.domain.FlightSeatGradePrice;
import com.siwol025.flight_monitor.subscription.domain.flight.Flight;
import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import com.siwol025.flight_monitor.subscription.service.FlightFetcher;
import com.siwol025.flight_monitor.subscription.service.FlightService;
import java.math.BigDecimal;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceMonitorService {

    private final StringRedisTemplate redisTemplate;
    private final FlightFetcher flightFetcher;
    private final FlightService flightService;
    private final FlightSeatGradePriceService flightSeatGradePriceService;

    public void checkAndUpdatePrice(Long flightId, SeatGrade seatGrade) {
        String redisKey = "flight:price:" + flightId + ":" + seatGrade.name();
        String cachedPriceStr = redisTemplate.opsForValue().get(redisKey);

        MockFlightResponse latestInfo = flightFetcher.fetchMockFlight(flightId);
        BigDecimal latestPrice = latestInfo.getPriceBySeatClass(seatGrade);

        if (cachedPriceStr != null) {
            if (new BigDecimal(cachedPriceStr).compareTo(latestPrice) == 0) {
                return;
            }
        }

        Flight flight = flightService.getFlight(flightId);
        FlightSeatGradePrice flightSeatGradePrice = flightSeatGradePriceService.findOrCreateFlightSeatGradePrice(
                flight, seatGrade, latestPrice);
        BigDecimal oldPriceInDb = flightSeatGradePrice.getPrice();

        // 가격 하락 알림 (좌석 등급 정보 포함)
        if (oldPriceInDb.compareTo(latestPrice) != 0 && latestPrice.compareTo(oldPriceInDb) < 0) {
            // todo: 비동기 알림(메일 발송) 기능 실행
        }

        flightSeatGradePrice.updatePrice(latestPrice);

        redisTemplate.opsForValue().set(redisKey, latestPrice.toString(), Duration.ofHours(24));
    }
}
