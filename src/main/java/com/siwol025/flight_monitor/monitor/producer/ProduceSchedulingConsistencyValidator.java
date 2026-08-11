package com.siwol025.flight_monitor.monitor.producer;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 프로듀서 스케줄링 일관성 가드.
 *
 * <p>불변식 1: {@code monitor.produce.lock-at-least-for} 는 {@code monitor.produce.fixed-rate-ms}
 * (스케줄 주기) 보다 짧아야 한다("주기보다 약간 짧게"). lockAtLeastFor 가 주기 이상이면 ShedLock 이
 * 락을 놓지 않아 다음 주기를 조용히 건너뛰고, {@link FlightMonitoringProducer} 의
 * "주기당 정확히 1회 발행" 불변식을 깨뜨린다.
 *
 * <p>불변식 2: {@code monitor.produce.lock-at-most-for}(리더 사망 시 락 자동 만료 안전망) 는
 * {@code lock-at-least-for} 이상이어야 한다. 안전망이 최소 보유 시간보다 짧으면 두 값의 의미가 뒤집힌다.
 *
 * <p>둘 중 하나라도 깨진 설정이면 오조작이 런타임에 조용히 처리량을 갉아먹기 전에
 * 빈 초기화 단계에서 fail-fast 한다. 이 불변식은
 * {@code ProduceSchedulingConsistencyGuardTest} 가 가드한다.
 */
@Component
public class ProduceSchedulingConsistencyValidator {

    private final long fixedRateMs;
    private final Duration lockAtLeastFor;
    private final Duration lockAtMostFor;

    public ProduceSchedulingConsistencyValidator(
            @Value("${monitor.produce.fixed-rate-ms}") long fixedRateMs,
            @Value("${monitor.produce.lock-at-least-for}") Duration lockAtLeastFor,
            @Value("${monitor.produce.lock-at-most-for}") Duration lockAtMostFor) {
        this.fixedRateMs = fixedRateMs;
        this.lockAtLeastFor = lockAtLeastFor;
        this.lockAtMostFor = lockAtMostFor;

        if (lockAtLeastFor.toMillis() >= fixedRateMs) {
            throw new IllegalStateException(
                    "monitor.produce.lock-at-least-for(" + lockAtLeastFor
                            + ")는 monitor.produce.fixed-rate-ms(" + fixedRateMs
                            + "ms) 보다 짧아야 한다. 락 보유 시간이 주기 이상이면 ShedLock 이 주기를 조용히 건너뛰어"
                            + " \"주기당 정확히 1회 발행\" 불변식을 깨뜨린다.");
        }
        if (lockAtMostFor.compareTo(lockAtLeastFor) < 0) {
            throw new IllegalStateException(
                    "monitor.produce.lock-at-most-for(" + lockAtMostFor
                            + ")는 monitor.produce.lock-at-least-for(" + lockAtLeastFor
                            + ") 이상이어야 한다.");
        }
    }
}
