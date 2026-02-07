package com.siwol025.flight_monitor.monitor.service;

import com.siwol025.flight_monitor.monitor.domain.FlightSeatGradePrice;
import com.siwol025.flight_monitor.monitor.repository.FlightSeatGradePriceRepository;
import com.siwol025.flight_monitor.subscription.domain.flight.Flight;
import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FlightSeatGradePriceService {

    private final FlightSeatGradePriceRepository flightSeatGradePriceRepository;

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
}
