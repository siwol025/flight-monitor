package com.siwol025.flight_monitor.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.siwol025.flight_monitor.monitor.metrics.PipelineMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 거부 도달-불가(rejection-unreachable) 불변식 가드:
 * {@code monitor.executor.queue-capacity >= monitor.worker.count} 가 깨지면
 * (큐 용량이 워커 수보다 작으면) 스프링 컨텍스트가 기동 단계에서 fail-fast 로 실패해야 한다.
 *
 * <p>현재는 검증이 없어 out-of-range 설정으로도 컨텍스트가 정상 기동하고
 * CallerRuns 로 조용히 처리량이 저하된다. 이 테스트는 그 공백을 드러낸다.
 */
class MonitoringExecutorRejectionUnreachableGuardTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AsyncConfig.class)
            .withBean(PipelineMetrics.class, () -> mock(PipelineMetrics.class));

    @Test
    void 컨텍스트기동_큐용량이워커수보다작으면_실패한다() {
        contextRunner
                .withPropertyValues(
                        "monitor.executor.queue-capacity=2",
                        "monitor.worker.count=3")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("queue-capacity >= worker.count"));
    }
}
