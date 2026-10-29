package com.siwol025.flight_monitor.monitor.worker;

import com.siwol025.flight_monitor.monitor.metrics.PipelineMetrics;
import com.siwol025.flight_monitor.monitor.service.PriceMonitorService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MonitoringTaskProcessorTest {

    @Mock
    private PriceMonitorService priceMonitorService;

    @Mock
    private PipelineMetrics pipelineMetrics;

    @Mock
    private InFlightBackpressure backpressure;

    @InjectMocks
    private MonitoringTaskProcessor processor;

    @Test
    void processTask_정상페이로드_priceMonitorService_호출됨() {
        // when: 큐 페이로드는 노선 ID 숫자 문자열이다
        processor.processTask("42");

        // then
        verify(priceMonitorService).checkPriceAndNotify(42L);
    }

    @Test
    void processTask_잘못된페이로드_예외_삼키고_priceMonitorService_미호출() {
        // given
        String invalidPayload = "{\"flightId\":42,\"seatGrade\":\"ECONOMY\"}";

        // when & then
        assertDoesNotThrow(() -> processor.processTask(invalidPayload));
        verify(priceMonitorService, never()).checkPriceAndNotify(any());
    }

    @Test
    void processTask_정상처리시_PipelineMetrics_계측됨() {
        // given
        PriceMonitorService mockedPriceMonitorService = mock(PriceMonitorService.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PipelineMetrics pipelineMetrics = new PipelineMetrics(registry);
        MonitoringTaskProcessor instrumentedProcessor =
                new MonitoringTaskProcessor(mockedPriceMonitorService, pipelineMetrics, mock(InFlightBackpressure.class));

        // priceMonitorService 처리 도중에는 in-flight 게이지가 1이어야 한다
        willAnswer(invocation -> {
            assertThat(registry.get(PipelineMetrics.METRIC_TASK_INFLIGHT).gauge().value()).isEqualTo(1.0);
            return null;
        }).given(mockedPriceMonitorService).checkPriceAndNotify(any());

        // when
        instrumentedProcessor.processTask("42");

        // then
        assertThat(registry.get(PipelineMetrics.METRIC_TASK_PROCESSED).counter().count()).isEqualTo(1.0);
        assertThat(registry.get(PipelineMetrics.METRIC_TASK_LATENCY).timer().count()).isEqualTo(1L);
        assertThat(registry.get(PipelineMetrics.METRIC_TASK_INFLIGHT).gauge().value()).isEqualTo(0.0);
    }

    @Test
    void processTask_정상처리완료시_백프레셔_permit_반환됨() {
        // when
        processor.processTask("42");

        // then
        verify(backpressure).release();
    }

    @Test
    void processTask_처리중_예외발생해도_백프레셔_permit_반환됨() {
        // when
        processor.processTask("not-a-route-id");

        // then
        verify(backpressure).release();
    }
}
