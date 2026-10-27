package com.siwol025.flight_monitor.monitor.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

/**
 * 노선별 최신 최저가에 관측 하나를 기록한 결과.
 *
 * @param droppedFrom     가격이 내려갔으면 이전 금액
 * @param refreshInterval 직전 관측 시각부터 이번 관측 시각까지(노선 갱신 간격). 첫 관측이면 empty
 */
public record FareObservationResult(Optional<BigDecimal> droppedFrom, Optional<Duration> refreshInterval) {
}
