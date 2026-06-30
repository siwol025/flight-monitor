package com.siwol025.flight_monitor.monitor.worker;

import com.siwol025.flight_monitor.monitor.utils.TaskQueueConsumerManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.timeout;

@ExtendWith(MockitoExtension.class)
class FlightMonitoringWorkerTest {

    @Mock
    private TaskQueueConsumerManager queueManager;

    @Mock
    private MonitoringTaskProcessor taskProcessor;

    private FlightMonitoringWorker worker;

    @AfterEach
    void tearDown() {
        if (worker != null) {
            worker.stopWorkers();
        }
    }

    @Test
    void startWorkers_후_태스크_수신시_MonitoringTaskProcessor에_위임됨() {
        // given
        given(queueManager.blockAndPopTask(3))
                .willReturn("task-payload")
                .willReturn(null);
        given(queueManager.hasPendingFallbackTask()).willReturn(false);

        worker = new FlightMonitoringWorker(queueManager, taskProcessor);

        // when
        worker.startWorkers();

        // then
        then(taskProcessor).should(timeout(2000)).processTask("task-payload");
    }

    @Test
    void stopWorkers_호출시_예외없이_종료됨() {
        // given
        given(queueManager.blockAndPopTask(3)).willReturn(null);

        worker = new FlightMonitoringWorker(queueManager, taskProcessor);
        worker.startWorkers();

        // when & then
        assertDoesNotThrow(() -> {
            Thread.sleep(50);
            worker.stopWorkers();
        });
    }
}
