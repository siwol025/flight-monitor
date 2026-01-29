package com.siwol025.flight_monitor.mock.airport.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "mock_airports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MockAirport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String airportCode;

    @Column(nullable = false)
    private String airportName;

    @Builder
    public MockAirport(String airportCode, String airportName) {
        this.airportCode = airportCode;
        this.airportName = airportName;
    }

    public void updateName(String name) {
        this.airportName = name;
    }
}
