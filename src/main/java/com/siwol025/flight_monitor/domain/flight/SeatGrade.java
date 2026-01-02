package com.siwol025.flight_monitor.domain.flight;

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
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SeatGrade {
    ECONOMY("이코노미"),
    BUSINESS("비즈니스"),
    FIRST("퍼스트");

    private final String description;
}
