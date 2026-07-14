package com.siwol025.flight_monitor.monitor.metrics;

import com.siwol025.flight_monitor.monitor.utils.TaskQueueManager;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class QueueDepthGaugeRegistrar {

    private final PipelineMetrics pipelineMetrics;
    private final TaskQueueManager taskQueueManager;

    public QueueDepthGaugeRegistrar(PipelineMetrics pipelineMetrics, TaskQueueManager taskQueueManager) {
        this.pipelineMetrics = pipelineMetrics;
        this.taskQueueManager = taskQueueManager;
    }

    @PostConstruct
    public void registerQueueDepthGauge() {
        pipelineMetrics.registerQueueDepthGauge(taskQueueManager::getQueueSizeSafely);
    }
}
