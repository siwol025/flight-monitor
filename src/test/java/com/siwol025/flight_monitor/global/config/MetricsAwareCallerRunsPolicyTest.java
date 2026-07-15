package com.siwol025.flight_monitor.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.siwol025.flight_monitor.monitor.metrics.PipelineMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class MetricsAwareCallerRunsPolicyTest {

    @Test
    void rejectedExecution_거부카운터증가하고_태스크는_호출스레드에서_실행된다() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PipelineMetrics pipelineMetrics = new PipelineMetrics(registry);
        MetricsAwareCallerRunsPolicy handler = new MetricsAwareCallerRunsPolicy(pipelineMetrics);

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS, new SynchronousQueue<>());
        Thread callerThread = Thread.currentThread();
        AtomicBoolean ran = new AtomicBoolean(false);
        AtomicReference<Thread> executedOnThread = new AtomicReference<>();
        Runnable task = () -> {
            ran.set(true);
            executedOnThread.set(Thread.currentThread());
        };

        try {
            handler.rejectedExecution(task, executor);

            assertThat(ran.get()).isTrue();
            assertThat(executedOnThread.get()).isEqualTo(callerThread);
            assertThat(registry.get(PipelineMetrics.METRIC_TASK_REJECTED).counter().count())
                    .isEqualTo(1.0);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void rejectedExecution_executor가_shutdown상태여도_거부카운터는_증가한다() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PipelineMetrics pipelineMetrics = new PipelineMetrics(registry);
        MetricsAwareCallerRunsPolicy handler = new MetricsAwareCallerRunsPolicy(pipelineMetrics);

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS, new SynchronousQueue<>());
        executor.shutdown();
        AtomicBoolean ran = new AtomicBoolean(false);
        Runnable task = () -> ran.set(true);

        handler.rejectedExecution(task, executor);

        assertThat(ran.get()).isFalse();
        assertThat(registry.get(PipelineMetrics.METRIC_TASK_REJECTED).counter().count())
                .isEqualTo(1.0);
    }
}
