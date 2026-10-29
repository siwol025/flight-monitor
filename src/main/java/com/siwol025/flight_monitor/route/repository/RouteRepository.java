package com.siwol025.flight_monitor.route.repository;

import com.siwol025.flight_monitor.route.domain.Route;
import com.siwol025.flight_monitor.route.domain.SeatGrade;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RouteRepository extends JpaRepository<Route, Long> {

    Optional<Route> findByDepartureAirportAndArrivalAirportAndDepartureDateAndSeatGrade(
            String departureAirport, String arrivalAirport, LocalDate departureDate, SeatGrade seatGrade);
}
