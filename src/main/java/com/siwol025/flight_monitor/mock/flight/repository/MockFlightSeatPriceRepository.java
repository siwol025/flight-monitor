package com.siwol025.flight_monitor.mock.flight.repository;

import com.siwol025.flight_monitor.mock.flight.domain.FlightSeatPrice;
import com.siwol025.flight_monitor.route.domain.SeatGrade;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface MockFlightSeatPriceRepository extends JpaRepository<FlightSeatPrice, Long> {

    // Duffel 단건 offer 조회용: 가격 행 하나와 offer를 만드는 데 필요한 항공편·항공사·공항을 한 번에 읽는다
    @Query("SELECT p FROM FlightSeatPrice p " +
            "JOIN FETCH p.flight f " +
            "JOIN FETCH f.airline " +
            "JOIN FETCH f.departureAirport " +
            "JOIN FETCH f.arrivalAirport " +
            "WHERE p.id = :id")
    Optional<FlightSeatPrice> findWithFlightDetailsById(Long id);

    Optional<FlightSeatPrice> findByFlightIdAndSeatGrade(Long flightId, SeatGrade seatGrade);

    @Query("Select distinct f FROM FlightSeatPrice f " +
            "JOIN FETCH f.flight"
    )
    List<FlightSeatPrice> findAllWithFlightNumber();
}
