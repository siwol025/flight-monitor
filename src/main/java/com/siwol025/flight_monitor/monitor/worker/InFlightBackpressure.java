package com.siwol025.flight_monitor.monitor.worker;

import com.siwol025.flight_monitor.global.exception.custom.AcquireInterruptedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;

/**
 * 컨슈머 디스패치 백프레셔 게이트.
 *
 * <p>세마포어로 동시 처리 수(in-flight)를 상한한다. 디스패치 전 {@link #acquire()}로 permit을 획득하고,
 * 처리 완료 후 {@link #release()}로 반납한다. permit 총량({@code monitor.backpressure.max-in-flight})이
 * in-flight 상한의 단일 권위다. 외부 API 전역 rate 한도는 {@link RedisRateLimiter}가 pop 게이팅으로 함께 준수한다.</p>
 */
@Component
@Profile("!mock")
public class InFlightBackpressure {

    private final Semaphore semaphore;
    private final int maxInFlight;
    // 외부 API 전역 rate 한도 준수용 분산 rate limiter. 비활성 시 null → rate 게이팅을 건너뛴다.
    @Autowired(required = false)
    private RedisRateLimiter rateLimiter;

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
     * 디스패치 전 in-flight permit을 획득하고(획득 가능할 때까지 블로킹), 이어서 rate 토큰을 획득한다.
     *
     * <p>대기 중 인터럽트가 발생하면 인터럽트 플래그를 복원하고 {@link AcquireInterruptedException}을 던진다.
     * 이때는 permit을 보유하지 않은 상태이므로(rate 대기 중 인터럽트면 이미 획득한 permit을 반납한다)
     * 호출자는 {@code release()}를 호출하면 안 된다.</p>
     */
    public void acquire() {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AcquireInterruptedException(e);
        }
        if (rateLimiter != null) {
            try {
                rateLimiter.acquire();
            } catch (AcquireInterruptedException e) {
                // rate 토큰 대기 중 인터럽트: 이미 permit을 보유 중이므로 반납 후 전파(permit 누수 방지).
                semaphore.release();
                throw e;
            }
        }
    }

    /**
     * 처리 완료 후 permit을 반납한다.
     */
    public void release() {
        semaphore.release();
    }
}
