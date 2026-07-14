package com.siwol025.flight_monitor.monitor.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.siwol025.flight_monitor.monitor.utils.TaskQueueManager;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QueueDepthGaugeRegistrarTest {

    @Mock
    private TaskQueueManager taskQueueManager;

    @Test
    void registerQueueDepthGauge_호출시_큐사이즈소스값이_게이지에_반영된다() {
        // given
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PipelineMetrics pipelineMetrics = new PipelineMetrics(registry);
        QueueDepthGaugeRegistrar registrar = new QueueDepthGaugeRegistrar(pipelineMetrics, taskQueueManager);

        given(taskQueueManager.getQueueSizeSafely()).willReturn(7L);

        // when
        registrar.registerQueueDepthGauge();

        // then
        assertThat(registry.get(PipelineMetrics.METRIC_QUEUE_DEPTH).gauge().value()).isEqualTo(7.0);
    }
}
