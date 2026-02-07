package com.siwol025.flight_monitor.monitor.repository;

import com.siwol025.flight_monitor.monitor.domain.FlightSeatGradePrice;
import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlightSeatGradePriceRepository extends JpaRepository<FlightSeatGradePrice, Long> {

    Optional<FlightSeatGradePrice> findByFlightIdAndSeatGrade(Long flightId, SeatGrade seatGrade);
}
