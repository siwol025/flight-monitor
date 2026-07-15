package com.siwol025.flight_monitor.global.config;

import com.siwol025.flight_monitor.monitor.metrics.PipelineMetrics;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * {@link ThreadPoolExecutor.CallerRunsPolicy} 와 동일하게 거부된 작업을 호출 스레드에서 실행하되,
 * 백프레셔(큐 포화) 발생을 {@link PipelineMetrics#recordRejected()} 로 계측한다.
 */
public class MetricsAwareCallerRunsPolicy implements RejectedExecutionHandler {

    private final PipelineMetrics pipelineMetrics;

    public MetricsAwareCallerRunsPolicy(PipelineMetrics pipelineMetrics) {
        this.pipelineMetrics = pipelineMetrics;
    }

    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        pipelineMetrics.recordRejected();
        if (!executor.isShutdown()) {
            r.run();
        }
    }
}
