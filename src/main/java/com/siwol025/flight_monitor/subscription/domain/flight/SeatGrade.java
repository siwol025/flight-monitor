package com.siwol025.flight_monitor.subscription.domain.flight;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SeatGrade {
    ECONOMY("이코노미"),
    BUSINESS("비즈니스"),
    FIRST("퍼스트");

    private final String description;
}
