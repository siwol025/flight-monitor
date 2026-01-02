package com.siwol025.flight_monitor.domain.airline;

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
@Table(name = "airlines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Airline {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String airlineCode;

    @Column(nullable = false)
    private String airlineName;

    @Builder
    public Airline(String airlineCode, String airlineName) {
        this.airlineCode = airlineCode;
        this.airlineName = airlineName;
    }
}
