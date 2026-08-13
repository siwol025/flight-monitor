package com.siwol025.flight_monitor.monitor.worker;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;

/**
 * 컨슈머 디스패치 백프레셔 게이트.
 *
 * <p>고정 TPS RateLimiter를 대체하는 세마포어 기반 in-flight 백프레셔 컴포넌트다.
 * 디스패치 전 {@link #acquire()}로 permit을 획득하고, 처리 완료 후 {@link #release()}로 반납한다.</p>
 */
@Component
@Profile("!mock")
public class InFlightBackpressure {

    private final Semaphore semaphore;
    private final int maxInFlight;

    // 세마포어 permit 총량 — in-flight 상한의 단일 권위.
    // monitoringTaskExecutor는 가상 스레드 per-task executor(풀 max/큐/거부 없음)라 정렬할 풀 크기가 없다.
    public InFlightBackpressure(@Value("${monitor.backpressure.max-in-flight:100}") int maxInFlight) {
        this.maxInFlight = maxInFlight;
        this.semaphore = new Semaphore(maxInFlight);
    }

    /**
     * 세마포어 permit 총량(= 설정된 max-in-flight)을 반환한다.
     */
    public int getMaxInFlight() {
        return maxInFlight;
    }

    /**
     * 디스패치 전 in-flight permit을 획득한다(획득 가능할 때까지 블로킹).
     *
     * <p>대기 중 인터럽트가 발생하면 인터럽트 플래그를 복원하고 {@link AcquireInterruptedException}을 던진다.
     * 이때는 permit을 보유하지 않은 상태이므로 호출자는 {@code release()}를 호출하면 안 된다.</p>
     */
    public void acquire() {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AcquireInterruptedException(e);
        }
    }

    /**
     * 처리 완료 후 permit을 반납한다.
     */
    public void release() {
        semaphore.release();
    }
}
