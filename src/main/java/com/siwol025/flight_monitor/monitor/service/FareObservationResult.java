package com.siwol025.flight_monitor.monitor.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

/**
 * 노선별 최신 최저가에 관측 하나를 기록한 결과.
 *
 * <p>첫 관측(저장된 최신가가 없음)이면 두 필드 모두 empty다. 새 가격(내려간 뒤 금액)은 담지 않는다: 호출자가
 * {@code record}에 넘긴 {@code LowestFare}를 이미 갖고 있고, {@link PriceMonitorService}가 이전 금액과 새 가격을
 * 함께 하락 이벤트에 싣는다.</p>
 *
 * @param droppedFrom     같은 통화에서 금액이 내려갔을 때만 이전 금액. 첫 관측·동일·상승·통화 변경이면 empty
 * @param refreshInterval 직전 관측 시각부터 이번 관측 시각까지(노선 갱신 간격). 기존 행이 있으면 동일·상승·하락·통화
 *                        변경 모든 경우에 채우고, 첫 관측이면 empty
 */
public record FareObservationResult(Optional<BigDecimal> droppedFrom, Optional<Duration> refreshInterval) {
}
