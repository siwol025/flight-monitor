package com.siwol025.flight_monitor.domain.flight;

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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "seats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flight_id")
    private Flight flight;

    @Enumerated(EnumType.STRING)
    @Column(name = "grade_name", nullable = false)
    private SeatGrade seatGrade;

    @Column(nullable = false, length = 10)
    private String seatNumber;

    @Column(nullable = false)
    private boolean isBooked = false;

    @Builder
    public Seat(Flight flight, SeatGrade seatGrade, String seatNumber, boolean isBooked) {
        this.flight = flight;
        this.seatGrade = seatGrade;
        this.seatNumber = seatNumber;
        this.isBooked = isBooked;
    }

    // 비즈니스 로직: 좌석 예약 상태 변경
    public void reserve() {
        this.isBooked = true;
    }

    public void cancelReservation() {
        this.isBooked = false;
    }
}
