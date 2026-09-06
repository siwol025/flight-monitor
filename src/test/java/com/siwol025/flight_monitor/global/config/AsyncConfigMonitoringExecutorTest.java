package com.siwol025.flight_monitor.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.siwol025.flight_monitor.monitor.metrics.PipelineMetrics;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 가드: monitoringTaskExecutor에 제출된 태스크가 플랫폼 스레드풀(MonitorWorker-)에서 실행되는지 검증한다.
 * 동시 처리 수 상한은 InFlightBackpressure 세마포어가 결정하고, 이 풀은 그 상한만큼의 플랫폼 스레드로 처리한다.
 */
@SpringBootTest(classes = {AsyncConfig.class})
class AsyncConfigMonitoringExecutorTest {

    @MockitoBean
    private PipelineMetrics pipelineMetrics;

    @Autowired
    @Qualifier("monitoringTaskExecutor")
    private Executor monitoringTaskExecutor;

    @Test
    void 제출된태스크_플랫폼스레드풀에서_실행됨() throws Exception {
        CompletableFuture<Boolean> virtualFuture = new CompletableFuture<>();
        CompletableFuture<String> nameFuture = new CompletableFuture<>();

        monitoringTaskExecutor.execute(() -> {
            virtualFuture.complete(Thread.currentThread().isVirtual());
            nameFuture.complete(Thread.currentThread().getName());
        });

        assertThat(virtualFuture.get(5, TimeUnit.SECONDS))
                .as("monitoringTaskExecutor 태스크는 플랫폼 스레드에서 실행되어야 한다")
                .isFalse();
        assertThat(nameFuture.get(5, TimeUnit.SECONDS))
                .as("monitoringTaskExecutor 스레드 이름은 MonitorWorker- 접두어를 가져야 한다")
                .startsWith("MonitorWorker-");
    }
}
