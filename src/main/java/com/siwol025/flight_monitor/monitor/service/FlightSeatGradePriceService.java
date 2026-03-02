package com.siwol025.flight_monitor.monitor.service;

import com.siwol025.flight_monitor.mock.flight.dto.response.MockFlightResponse;
import com.siwol025.flight_monitor.monitor.domain.FlightSeatGradePrice;
import com.siwol025.flight_monitor.monitor.dto.PriceDropNotificationDto;
import com.siwol025.flight_monitor.monitor.producer.NotificationProducer;
import com.siwol025.flight_monitor.monitor.repository.FlightSeatGradePriceRepository;
import com.siwol025.flight_monitor.subscription.domain.flight.Flight;
import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FlightSeatGradePriceService {

    private final FlightSeatGradePriceRepository flightSeatGradePriceRepository;
    private final StringRedisTemplate redisTemplate;
    private final NotificationProducer producer;

    @Transactional
    public FlightSeatGradePrice findOrCreateFlightSeatGradePrice(Flight flight, SeatGrade seatGrade, BigDecimal price) {
        return flightSeatGradePriceRepository.findByFlightIdAndSeatGrade(flight.getId(), seatGrade)
                .orElseGet(() -> flightSeatGradePriceRepository.save(
                   FlightSeatGradePrice.builder()
                           .flight(flight)
                           .seatGrade(seatGrade)
                           .price(price)
                           .build()
                ));
    }

    @Transactional
    public void processPublishAndPriceUpdate(Flight flight, SeatGrade seatGrade, BigDecimal currentPrice, MockFlightResponse latestInfo, String redisKey) {
        FlightSeatGradePrice flightSeatGradePrice = findOrCreateFlightSeatGradePrice(flight, seatGrade, currentPrice);
        BigDecimal oldPrice = flightSeatGradePrice.getPrice();

        if (isPriceDropped(currentPrice, oldPrice)) {
            PriceDropNotificationDto priceDropNotificationDto = PriceDropNotificationDto.builder()
                    .flightId(latestInfo.id())
                    .flightNumber(latestInfo.flightNumber())
                    .seatGrade(seatGrade)
                    .oldPrice(oldPrice)
                    .newPrice(currentPrice)
                    .detectedAt(LocalDateTime.now())
                    .build();

            producer.publishPriceDrop(priceDropNotificationDto);
        }

        flightSeatGradePrice.updatePrice(currentPrice);
        redisTemplate.opsForValue().set(redisKey, currentPrice.toString(), Duration.ofHours(24));
    }

    private boolean isPriceDropped(BigDecimal currentPrice, BigDecimal oldPrice) {
        return currentPrice.compareTo(oldPrice) < 0;
    }
}
