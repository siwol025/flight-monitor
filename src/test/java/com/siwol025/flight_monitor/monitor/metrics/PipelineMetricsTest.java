package com.siwol025.flight_monitor.monitor.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static com.siwol025.flight_monitor.monitor.metrics.PipelineMetrics.METRIC_EXTERNAL_API_LATENCY;
import static com.siwol025.flight_monitor.monitor.metrics.PipelineMetrics.METRIC_TASK_PROCESSED;
import static com.siwol025.flight_monitor.monitor.metrics.PipelineMetrics.METRIC_TASK_REJECTED;
import static com.siwol025.flight_monitor.monitor.metrics.PipelineMetrics.METRIC_TASK_LATENCY;
import static com.siwol025.flight_monitor.monitor.metrics.PipelineMetrics.METRIC_TASK_INFLIGHT;
import static com.siwol025.flight_monitor.monitor.metrics.PipelineMetrics.METRIC_QUEUE_DEPTH;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
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

    @Test
    void PipelineMetrics_지연타이머_Prometheus히스토그램_버킷노출() {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        PipelineMetrics pipelineMetrics = new PipelineMetrics(registry);

        pipelineMetrics.recordExternalApiLatency(Duration.ofMillis(120));
        pipelineMetrics.recordTaskLatency(Duration.ofMillis(80));

        String scraped = registry.scrape();

        assertThat(scraped).contains("pipeline_external_api_latency_seconds_bucket");
        assertThat(scraped).contains("pipeline_task_latency_seconds_bucket");
    }

    @Test
    void 갱신간격지표_30분넘는간격도_유한버킷에집계() {
        // 갱신 간격은 분 단위다. 타이머 히스토그램 기본 범위(30초까지)면 그보다 긴 간격이 +Inf 버킷에만 들어가 분위수를 못 구한다
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        PipelineMetrics pipelineMetrics = new PipelineMetrics(registry);

        pipelineMetrics.recordRouteRefreshInterval(Duration.ofMinutes(40));

        List<String> finiteBucketsWithObservation = Pattern
                .compile("pipeline_route_refresh_interval_seconds_bucket\\{[^}]*le=\"([^\"]+)\"[^}]*} (\\S+)")
                .matcher(registry.scrape())
                .results()
                .filter(bucket -> !bucket.group(1).equals("+Inf") && Double.parseDouble(bucket.group(2)) > 0)
                .map(bucket -> bucket.group(1))
                .toList();
        assertThat(finiteBucketsWithObservation).isNotEmpty();
    }
}
