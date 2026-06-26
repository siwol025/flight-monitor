package com.siwol025.flight_monitor.monitor.service;

import com.siwol025.flight_monitor.global.exception.ErrorTag;
import com.siwol025.flight_monitor.global.exception.custom.NotFoundException;
import com.siwol025.flight_monitor.monitor.domain.FlightLatestPriceInfo;
import com.siwol025.flight_monitor.monitor.repository.FlightLatestPriceInfoRepository;
import com.siwol025.flight_monitor.subscription.domain.flight.Flight;
import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import com.siwol025.flight_monitor.subscription.service.FlightService;
import java.math.BigDecimal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FlightLatestPriceInfoService {

    private final FlightLatestPriceInfoRepository flightLatestPriceInfoRepository;
    private final FlightService flightService;

    @Transactional
    public FlightLatestPriceInfo createLatestPriceInfo(Long flightId, SeatGrade seatGrade, BigDecimal price) {
        Flight flight = flightService.getFlight(flightId);
        FlightLatestPriceInfo flightLatestPriceInfo = FlightLatestPriceInfo.builder()
                .flight(flight)
                .seatGrade(seatGrade)
                .price(price)
                .build();
        return flightLatestPriceInfoRepository.save(flightLatestPriceInfo);
    }

    @Transactional
    public void updateLatestPriceInfo(Long flightId, SeatGrade seatGrade,BigDecimal currentPrice) {
        FlightLatestPriceInfo flightLatestPriceInfo = getFlightLatestPriceInfo(flightId, seatGrade);
        flightLatestPriceInfo.updatePrice(currentPrice);
    }

    public Optional<BigDecimal> getPreviousPrice(Long flightId, SeatGrade seatGrade) {
        return flightLatestPriceInfoRepository.findByFlightIdAndSeatGrade(flightId, seatGrade)
                .map(FlightLatestPriceInfo::getPrice);
    }

    private FlightLatestPriceInfo getFlightLatestPriceInfo(Long flightId, SeatGrade seatGrade) {
        return flightLatestPriceInfoRepository.findByFlightIdAndSeatGrade(flightId, seatGrade)
                .orElseThrow(() -> new NotFoundException(ErrorTag.FLIGHT_PRICE_INFO_NOT_FOUND));
    }
}
