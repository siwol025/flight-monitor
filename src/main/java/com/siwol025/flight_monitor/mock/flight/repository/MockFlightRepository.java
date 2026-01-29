package com.siwol025.flight_monitor.mock.flight.repository;

import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import com.siwol025.flight_monitor.mock.flight.domain.MockFlight;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface MockFlightRepository extends JpaRepository<MockFlight, Long> {

    @Query("SELECT DISTINCT f FROM MockFlight f " +
            "JOIN FETCH f.airline " +
            "JOIN FETCH f.departureAirport " +
            "JOIN FETCH f.arrivalAirport " +
            "LEFT JOIN FETCH f.flightSeatPrices p " +
            "WHERE f.departureAirport.airportCode = :departureAirportCode " +
            "AND f.arrivalAirport.airportCode = :arrivalAirportCode " +
            "AND f.departureTime BETWEEN :startOfDay AND :endOfDay " +
            "AND p.seatGrade = :seatGrade")
    List<MockFlight> findFlightsWithDetails(
            String departureAirportCode,
            String arrivalAirportCode,
            LocalDateTime startOfDay,
            LocalDateTime endOfDay,
            SeatGrade seatGrade
    );

    @Query("SELECT DISTINCT f FROM MockFlight f " +
            "JOIN FETCH f.airline " +
            "JOIN FETCH f.departureAirport " +
            "JOIN FETCH f.arrivalAirport " +
            "LEFT JOIN FETCH f.flightSeatPrices")
    List<MockFlight> findAllWithPrices();

    boolean existsByFlightNumberAndDepartureTimeBetween(
            String flightNumber,
            LocalDateTime start,
            LocalDateTime end
    );
}
