package com.siwol025.flight_monitor.subscription.domain.flight;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "flights")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Flight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String flightNumber;

    @Column(nullable = false)
    private String airlineCode;

    @Column(name = "departure_airport_code", nullable = false)
    private String departureAirport;

    @Column(name = "arrival_airport_code", nullable = false)
    private String arrivalAirport;

    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;

    @Builder
    public Flight(String flightNumber, String airlineCode, String departureAirport, String arrivalAirport,
                  LocalDateTime departureTime, LocalDateTime arrivalTime) {
        this.flightNumber = flightNumber;
        this.airlineCode = airlineCode;
        this.departureAirport = departureAirport;
        this.arrivalAirport = arrivalAirport;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
    }
}
