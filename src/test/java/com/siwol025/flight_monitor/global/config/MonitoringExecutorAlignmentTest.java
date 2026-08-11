package com.siwol025.flight_monitor.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.siwol025.flight_monitor.monitor.metrics.PipelineMetrics;
import com.siwol025.flight_monitor.monitor.worker.InFlightBackpressure;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * C5 회귀 가드: in-flight 세마포어 permit 수와 monitoringTaskExecutor의 max pool size가
 * 동일한 외부화 설정({@code monitor.backpressure.max-in-flight})에서 파생되어 정렬되는지 검증한다.
 */
@SpringBootTest(classes = {AsyncConfig.class, InFlightBackpressure.class})
@TestPropertySource(properties = "monitor.backpressure.max-in-flight=77")
class MonitoringExecutorAlignmentTest {

    @MockitoBean
    private PipelineMetrics pipelineMetrics;

    @Autowired
    @Qualifier("monitoringTaskExecutor")
    private Executor monitoringTaskExecutor;

    @Autowired
    private InFlightBackpressure backpressure;

    @Test
    void 백프레셔_permit수_풀max와_정렬됨() {
        int poolMax = ((ThreadPoolTaskExecutor) monitoringTaskExecutor).getMaxPoolSize();

        assertThat(poolMax).isEqualTo(backpressure.getMaxInFlight());
    }
}
