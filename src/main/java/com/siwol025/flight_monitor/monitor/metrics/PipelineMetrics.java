package com.siwol025.flight_monitor.monitor.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class PipelineMetrics {

    public static final String METRIC_TASK_PROCESSED = "pipeline.task.processed";
    public static final String METRIC_EXTERNAL_API_LATENCY = "pipeline.external.api.latency";
    public static final String METRIC_TASK_REJECTED = "pipeline.task.rejected";
    public static final String METRIC_TASK_LATENCY = "pipeline.task.latency";
    public static final String METRIC_TASK_INFLIGHT = "pipeline.task.inflight";
    public static final String METRIC_QUEUE_DEPTH = "pipeline.queue.depth";

    private final MeterRegistry meterRegistry;
    private final Counter taskProcessedCounter;
    private final Timer externalApiLatencyTimer;
    private final Counter taskRejectedCounter;
    private final Timer taskLatencyTimer;
    private final AtomicInteger inFlight = new AtomicInteger(0);
    // Micrometer 게이지는 supplier를 WeakReference로만 보유하므로, GC로 회수되어 게이지 갱신이 멈추지 않도록 강한 참조를 유지한다.
    private Supplier<Number> queueDepthSupplier;

    public PipelineMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.taskProcessedCounter = Counter.builder(METRIC_TASK_PROCESSED).register(meterRegistry);
        this.externalApiLatencyTimer = Timer.builder(METRIC_EXTERNAL_API_LATENCY).register(meterRegistry);
        this.taskRejectedCounter = Counter.builder(METRIC_TASK_REJECTED).register(meterRegistry);
        this.taskLatencyTimer = Timer.builder(METRIC_TASK_LATENCY).register(meterRegistry);
        Gauge.builder(METRIC_TASK_INFLIGHT, inFlight, AtomicInteger::doubleValue).register(meterRegistry);
    }

    public void recordTaskProcessed() {
        taskProcessedCounter.increment();
    }

    public void recordExternalApiLatency(Duration duration) {
        externalApiLatencyTimer.record(duration);
    }

    public void recordRejected() {
        taskRejectedCounter.increment();
    }

    public void recordTaskLatency(Duration duration) {
        taskLatencyTimer.record(duration);
    }

    public void incrementInFlight() {
        inFlight.incrementAndGet();
    }

    public void decrementInFlight() {
        inFlight.decrementAndGet();
    }

    public void registerQueueDepthGauge(Supplier<Number> supplier) {
        this.queueDepthSupplier = supplier;
        Gauge.builder(METRIC_QUEUE_DEPTH, supplier, s -> s.get().doubleValue()).register(meterRegistry);
    }
}
