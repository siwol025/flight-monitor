package com.siwol025.flight_monitor.monitor.producer;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class FlightMonitoringProducerLockTest {

    @Test
    void 프로듀서_리더선출_SchedulerLock_선언됨() throws NoSuchMethodException {
        Method method = FlightMonitoringProducer.class.getDeclaredMethod("produceMonitoringTasks");

        SchedulerLock schedulerLock = method.getAnnotation(SchedulerLock.class);

        assertThat(schedulerLock)
                .as("produceMonitoringTasks 메서드에 @SchedulerLock 어노테이션이 없습니다. 리더 선출이 메서드 레벨로 승격되지 않았습니다.")
                .isNotNull();

        assertThat(schedulerLock.name())
                .as("@SchedulerLock name()은 안정적인 락 이름으로 비어있지 않아야 합니다.")
                .isNotBlank();

        assertThat(schedulerLock.lockAtLeastFor())
                .as("lockAtLeastFor는 설정 placeholder로 외부화되어야 합니다.")
                .isEqualTo("${monitor.produce.lock-at-least-for}");

        assertThat(schedulerLock.lockAtMostFor())
                .as("lockAtMostFor는 설정 placeholder로 외부화되어야 합니다.")
                .isEqualTo("${monitor.produce.lock-at-most-for}");
    }

    @Test
    void 프로듀서_주기_외부화됨() throws NoSuchMethodException {
        Method method = FlightMonitoringProducer.class.getDeclaredMethod("produceMonitoringTasks");

        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled)
                .as("produceMonitoringTasks 메서드에 @Scheduled 어노테이션이 없습니다.")
                .isNotNull();

        assertThat(scheduled.fixedRateString())
                .as("@Scheduled 주기는 fixedRateString placeholder로 외부화되어야 합니다.")
                .isEqualTo("${monitor.produce.fixed-rate-ms}");

        assertThat(scheduled.fixedRate())
                .as("fixedRate는 하드코딩되면 안 되며 미설정(-1) 상태여야 합니다.")
                .isEqualTo(-1L);
    }
}
