package com.siwol025.flight_monitor.monitor.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static com.siwol025.flight_monitor.monitor.metrics.PipelineMetrics.METRIC_EXTERNAL_API_LATENCY;
import static com.siwol025.flight_monitor.monitor.metrics.PipelineMetrics.METRIC_TASK_PROCESSED;
import static com.siwol025.flight_monitor.monitor.metrics.PipelineMetrics.METRIC_TASK_REJECTED;
import static com.siwol025.flight_monitor.monitor.metrics.PipelineMetrics.METRIC_TASK_LATENCY;
import static com.siwol025.flight_monitor.monitor.metrics.PipelineMetrics.METRIC_TASK_INFLIGHT;
import static com.siwol025.flight_monitor.monitor.metrics.PipelineMetrics.METRIC_QUEUE_DEPTH;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PipelineMetricsTest {

    @Test
    void 메트릭_태스크처리시_TPS카운터_증가() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PipelineMetrics pipelineMetrics = new PipelineMetrics(registry);

        pipelineMetrics.recordTaskProcessed();

        assertThat(registry.get(METRIC_TASK_PROCESSED).counter().count()).isEqualTo(1.0);
    }

    @Test
    void 메트릭_외부API조회_지연타이머_기록() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PipelineMetrics pipelineMetrics = new PipelineMetrics(registry);

        pipelineMetrics.recordExternalApiLatency(Duration.ofMillis(150));

        assertThat(registry.get(METRIC_EXTERNAL_API_LATENCY).timer().count()).isEqualTo(1L);
        assertThat(registry.get(METRIC_EXTERNAL_API_LATENCY).timer().totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
                .isEqualTo(150.0);
    }

    @Test
    void 메트릭_거부시_거부카운터_증가() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PipelineMetrics pipelineMetrics = new PipelineMetrics(registry);

        pipelineMetrics.recordRejected();

        assertThat(registry.get(METRIC_TASK_REJECTED).counter().count()).isEqualTo(1.0);
    }

    @Test
    void 메트릭_태스크처리지연_타이머_기록() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PipelineMetrics pipelineMetrics = new PipelineMetrics(registry);

        pipelineMetrics.recordTaskLatency(Duration.ofMillis(200));

        assertThat(registry.get(METRIC_TASK_LATENCY).timer().count()).isEqualTo(1L);
        assertThat(registry.get(METRIC_TASK_LATENCY).timer().totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
                .isEqualTo(200.0);
    }

    @Test
    void 메트릭_inflight_증감시_게이지_반영() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PipelineMetrics pipelineMetrics = new PipelineMetrics(registry);

        pipelineMetrics.incrementInFlight();
        pipelineMetrics.incrementInFlight();
        pipelineMetrics.decrementInFlight();

        assertThat(registry.get(METRIC_TASK_INFLIGHT).gauge().value()).isEqualTo(1.0);
    }

    @Test
    void 메트릭_큐깊이게이지_supplier값_반영() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PipelineMetrics pipelineMetrics = new PipelineMetrics(registry);
        AtomicInteger queueDepthHolder = new AtomicInteger(0);

        pipelineMetrics.registerQueueDepthGauge(queueDepthHolder::get);
        queueDepthHolder.set(7);

        assertThat(registry.get(METRIC_QUEUE_DEPTH).gauge().value()).isEqualTo(7.0);
    }
}
