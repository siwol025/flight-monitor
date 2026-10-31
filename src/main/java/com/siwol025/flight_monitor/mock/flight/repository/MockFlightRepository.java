package com.siwol025.flight_monitor.mock.flight.repository;

import com.siwol025.flight_monitor.route.domain.SeatGrade;
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

    // Duffel 검색용: findFlightsWithDetails에 "해당 등급 빈 좌석 있음(예매 가능)" 조건을 더한다. 관리 API는 매진편도 보여야 해서 따로 둔다
    @Query("SELECT DISTINCT f FROM MockFlight f " +
            "JOIN FETCH f.airline " +
            "JOIN FETCH f.departureAirport " +
            "JOIN FETCH f.arrivalAirport " +
            "LEFT JOIN FETCH f.flightSeatPrices p " +
            "WHERE f.departureAirport.airportCode = :departureAirportCode " +
            "AND f.arrivalAirport.airportCode = :arrivalAirportCode " +
            "AND f.departureTime BETWEEN :startOfDay AND :endOfDay " +
            "AND p.seatGrade = :seatGrade " +
            "AND EXISTS (SELECT s FROM Seat s WHERE s.flight = f AND s.seatGrade = :seatGrade AND s.isBooked = false)")
    List<MockFlight> findBookableFlightsWithDetails(
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
