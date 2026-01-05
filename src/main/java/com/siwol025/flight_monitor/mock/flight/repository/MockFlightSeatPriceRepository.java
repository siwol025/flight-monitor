package com.siwol025.flight_monitor.mock.flight.repository;

import com.siwol025.flight_monitor.domain.flight.FlightSeatPrice;
import com.siwol025.flight_monitor.domain.flight.SeatGrade;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MockFlightSeatPriceRepository extends JpaRepository<FlightSeatPrice, Long> {
    Optional<FlightSeatPrice> findByFlightIdAndSeatGrade(Long flightId, SeatGrade seatGrade);
}
