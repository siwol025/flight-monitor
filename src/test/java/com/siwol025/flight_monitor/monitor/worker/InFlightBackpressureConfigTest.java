package com.siwol025.flight_monitor.monitor.worker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.lang.reflect.Field;
import java.util.concurrent.Semaphore;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * config → 세마포어 배선 회귀 가드.
 *
 * <p>외부화된 {@code monitor.backpressure.max-in-flight} 값이 실제로 {@link InFlightBackpressure}
 * 세마포어 permit 총량에 반영되는지 end-to-end로 잠근다. permit 상향 전에 배선을 특성화(characterization)한다.</p>
 */
@SpringBootTest(classes = InFlightBackpressure.class)
@TestPropertySource(properties = "monitor.backpressure.max-in-flight=780")
class InFlightBackpressureConfigTest {

    @Autowired
    private InFlightBackpressure backpressure;

    @Test
    void 백프레셔_상향된permit수_세마포어_반영됨() throws Exception {
        assertThat(backpressure.getMaxInFlight()).isEqualTo(780);

        Field semaphoreField = InFlightBackpressure.class.getDeclaredField("semaphore");
        semaphoreField.setAccessible(true);
        Semaphore semaphore = (Semaphore) semaphoreField.get(backpressure);

        assertThat(semaphore.availablePermits()).isEqualTo(780);
    }
}
