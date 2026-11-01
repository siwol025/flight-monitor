package com.siwol025.flight_monitor.mock.flight.repository;

import com.siwol025.flight_monitor.mock.flight.domain.Seat;
import com.siwol025.flight_monitor.route.domain.SeatGrade;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface MockSeatRepository extends JpaRepository<Seat, Long> {
    @Query("SELECT distinct s FROM Seat s "
            + "JOIN FETCH s.flight "
            + "WHERE s.flight.id = :flightId "
            + "ORDER BY s.seatNumber")
    List<Seat> findSeatsByFlightIdOrderBySeatNumber(Long flightId);

    @Query("SELECT distinct s FROM Seat s "
            + "JOIN FETCH s.flight "
            + "WHERE s.flight.id = :flightId "
            + "AND s.seatGrade = :seatGrade "
            + "ORDER BY s.seatNumber")
    List<Seat> findSeatsByFlightIdAndSeatGrade(Long flightId, SeatGrade seatGrade);

    // 예매 가능 판정: 해당 항공편·등급에 빈 좌석이 하나라도 있는지(읽기만 한다)
    boolean existsByFlightIdAndSeatGradeAndIsBookedFalse(Long flightId, SeatGrade seatGrade);

    // 주문용: 해당 항공편·등급의 빈 좌석 중 좌석 번호(문자열 정렬)가 가장 낮은 것
    Optional<Seat> findFirstByFlightIdAndSeatGradeAndIsBookedFalseOrderBySeatNumberAsc(Long flightId, SeatGrade seatGrade);
}
