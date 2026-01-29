package com.siwol025.flight_monitor.mock.flight.domain;

import com.siwol025.flight_monitor.mock.airline.domain.MockAirline;
import com.siwol025.flight_monitor.mock.airport.domain.MockAirport;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "mock_flights")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MockFlight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String flightNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "airline_id")
    private MockAirline airline;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "departure_airport_id")
    private MockAirport departureAirport;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "arrival_airport_id")
    private MockAirport arrivalAirport;

    @OneToMany(mappedBy = "flight", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FlightSeatPrice> flightSeatPrices = new ArrayList<>();

    @BatchSize(size = 100)
    @OneToMany(mappedBy = "flight", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Seat> seats = new ArrayList<>();

    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;

    @Builder
    public MockFlight(String flightNumber, MockAirline airline, MockAirport departureAirport, MockAirport arrivalAirport,
                      LocalDateTime departureTime, LocalDateTime arrivalTime) {
        this.flightNumber = flightNumber;
        this.airline = airline;
        this.departureAirport = departureAirport;
        this.arrivalAirport = arrivalAirport;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
    }

    public void updateFlightInfo(String flightNumber, LocalDateTime departureTime, LocalDateTime arrivalTime) {
        this.flightNumber = flightNumber;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
    }

    public void addFlightSeatPrice(FlightSeatPrice flightSeatPrice) {
        this.flightSeatPrices.add(flightSeatPrice);
        flightSeatPrice.assignFlight(this);
    }

    public void addSeat(Seat seat) {
        this.seats.add(seat);
        seat.assignFlight(this);
    }
}
