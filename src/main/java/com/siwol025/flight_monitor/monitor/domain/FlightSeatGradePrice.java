package com.siwol025.flight_monitor.monitor.domain;

import com.siwol025.flight_monitor.mock.flight.domain.MockFlight;
import com.siwol025.flight_monitor.subscription.domain.flight.Flight;
import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "flight_seat_grade_prices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FlightSeatGradePrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flight_id")
    private Flight flight;

    @Enumerated(EnumType.STRING)
    @Column(name = "seat_grade", nullable = false)
    private SeatGrade seatGrade;

    @Column(nullable = false, precision = 12)
    private BigDecimal price;

    @Builder
    public FlightSeatGradePrice(Flight flight, SeatGrade seatGrade, BigDecimal price) {
        this.flight = flight;
        this.seatGrade = seatGrade;
        this.price = price;
    }

    public void updatePrice(BigDecimal price) {
        this.price = price;
    }
}
