package com.siwol025.flight_monitor.monitor.service;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 판정했지만 아직 최신가에 반영하지 않은 가격 하락. 이벤트 전달이 확인된 뒤 {@link RouteLatestFareService#applyDrop}으로 반영한다.
 *
 * @param previousAmount     판정 때 저장돼 있던 최신가(이벤트의 이전 금액)
 * @param previousObservedAt 판정 때 저장돼 있던 관측 시각. 반영은 최신가가 이 금액·시각 그대로일 때만 한다
 * @param observedAt         하락을 관측한 시각. 반영할 때 최신가의 관측 시각이 된다
 */
public record PendingDrop(BigDecimal previousAmount, Instant previousObservedAt, Instant observedAt) {
}
