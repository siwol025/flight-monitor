package com.siwol025.flight_monitor.mock.flight.repository;

import com.siwol025.flight_monitor.domain.flight.Flight;
import com.siwol025.flight_monitor.domain.flight.SeatGrade;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface MockFlightRepository extends JpaRepository<Flight, Long> {

    @Query("SELECT DISTINCT f FROM Flight f " +
            "JOIN FETCH f.airline " +
            "JOIN FETCH f.departureAirport " +
            "JOIN FETCH f.arrivalAirport " +
            "LEFT JOIN FETCH f.flightSeatPrices p " +
            "WHERE f.departureAirport.airportCode = :departureAirportCode " +
            "AND f.arrivalAirport.airportCode = :arrivalAirportCode " +
            "AND f.departureTime BETWEEN :startOfDay AND :endOfDay " +
            "AND p.seatGrade = :seatGrade")
    List<Flight> findFlightsWithDetails(
            String departureAirportCode,
            String arrivalAirportCode,
            LocalDateTime startOfDay,
            LocalDateTime endOfDay,
            SeatGrade seatGrade
    );

    @Query("SELECT DISTINCT f FROM Flight f " +
            "JOIN FETCH f.airline " +
            "JOIN FETCH f.departureAirport " +
            "JOIN FETCH f.arrivalAirport " +
            "LEFT JOIN FETCH f.flightSeatPrices")
    List<Flight> findAllWithPrices();

    boolean existsByFlightNumberAndDepartureTimeBetween(
            String flightNumber,
            LocalDateTime start,
            LocalDateTime end
    );
}
