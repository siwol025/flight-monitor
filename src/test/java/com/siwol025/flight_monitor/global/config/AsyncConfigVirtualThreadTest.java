package com.siwol025.flight_monitor.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.siwol025.flight_monitor.monitor.metrics.PipelineMetrics;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * RED 가드: monitoringTaskExecutor에 제출된 태스크가 가상 스레드에서 실행되는지 검증한다.
 * 블로킹 외부 호출이 플랫폼 스레드를 pin 하지 않도록 executor를 가상 스레드 기반으로 전환하는
 * 것이 목표 동작이다.
 */
@SpringBootTest(classes = {AsyncConfig.class})
class AsyncConfigVirtualThreadTest {

    @MockitoBean
    private PipelineMetrics pipelineMetrics;

    @Autowired
    @Qualifier("monitoringTaskExecutor")
    private Executor monitoringTaskExecutor;

    @Test
    void 가상스레드executor_제출된태스크_가상스레드에서_실행됨() throws Exception {
        java.util.concurrent.CompletableFuture<Boolean> virtualityFuture =
                new java.util.concurrent.CompletableFuture<>();

        monitoringTaskExecutor.execute(
                () -> virtualityFuture.complete(Thread.currentThread().isVirtual()));

        boolean ranOnVirtualThread = virtualityFuture.get(5, TimeUnit.SECONDS);

        assertThat(ranOnVirtualThread)
                .as("monitoringTaskExecutor에 제출된 태스크는 가상 스레드에서 실행되어야 한다")
                .isTrue();
    }
}
