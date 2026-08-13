package com.siwol025.flight_monitor.monitor.worker;

import com.siwol025.flight_monitor.monitor.utils.TaskQueueConsumerManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.timeout;

@ExtendWith(MockitoExtension.class)
class FlightMonitoringWorkerTest {

    @Mock
    private TaskQueueConsumerManager queueManager;

    @Mock
    private MonitoringTaskProcessor taskProcessor;

    @Mock
    private InFlightBackpressure backpressure;

    private FlightMonitoringWorker worker;

    /** 동기 실행 executor: hand-off 즉시 processTask 실행 → 기존 위임 검증 유지 */
    private static final Executor SYNC_EXECUTOR = Runnable::run;

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

        worker = new FlightMonitoringWorker(queueManager, taskProcessor, backpressure, SYNC_EXECUTOR, 1, 3);

        // when
        worker.startWorkers();

        // then
        then(taskProcessor).should(timeout(2000)).processTask("task-payload");
    }

    @Test
    void 태스크_수신시_백프레셔_permit_획득후_processTask에_위임됨() {
        // given
        given(queueManager.blockAndPopTask(3))
                .willReturn("task-payload")
                .willReturn(null);
        given(queueManager.hasPendingFallbackTask()).willReturn(false);

        worker = new FlightMonitoringWorker(queueManager, taskProcessor, backpressure, SYNC_EXECUTOR, 1, 3);

        // when
        worker.startWorkers();

        // then
        then(backpressure).should(timeout(2000).atLeastOnce()).acquire();
        then(taskProcessor).should(timeout(2000)).processTask("task-payload");
    }

    @Test
    void 백프레셔_in_flight상한_도달시_추가pop_보류됨() {
        // given: capacity 1 실제 백프레셔에서 유일한 permit을 테스트 스레드가 선점 (가용 permit 0)
        InFlightBackpressure realBackpressure = new InFlightBackpressure(1);
        realBackpressure.acquire();

        given(queueManager.blockAndPopTask(anyLong())).willReturn("task-payload");

        worker = new FlightMonitoringWorker(queueManager, taskProcessor, realBackpressure, SYNC_EXECUTOR, 1, 3);

        // when
        worker.startWorkers();

        // then: 상한 도달 상태에서는 permit 확보 전까지 pop을 보류해야 한다
        then(queueManager).should(after(500).never()).blockAndPopTask(anyLong());

        // when: permit 반환시 pop 재개
        realBackpressure.release();

        // then
        then(queueManager).should(timeout(1500).atLeastOnce()).blockAndPopTask(anyLong());
    }

    @Test
    void 백프레셔_async제출거부시_permit_반환됨_누수없음() {
        // given: executor가 제출을 거부(RejectedExecutionException)하는 상황
        // (payload는 항상 non-null → 빈-pop release 경로를 배제하고 거부 경로만 격리)
        given(queueManager.blockAndPopTask(anyLong())).willReturn("task-payload");

        Executor rejectingExecutor = command -> {
            throw new RejectedExecutionException("submit rejected");
        };

        worker = new FlightMonitoringWorker(queueManager, taskProcessor, backpressure, rejectingExecutor, 1, 3);

        // when
        worker.startWorkers();

        // then: 제출 거부에도 acquire한 permit은 반환되어야 한다(누수 없음)
        then(backpressure).should(timeout(2000).atLeastOnce()).release();
    }

    @Test
    void 백프레셔_acquire후_pop예외시_permit_반환됨() {
        // given: acquire 이후 pop 단계에서 예외 발생
        given(queueManager.blockAndPopTask(anyLong()))
                .willThrow(new RuntimeException("pop failed"));

        worker = new FlightMonitoringWorker(queueManager, taskProcessor, backpressure, SYNC_EXECUTOR, 1, 3);

        // when
        worker.startWorkers();

        // then: acquire한 permit은 예외 경로에서도 반환되어야 한다
        then(backpressure).should(timeout(2000).atLeastOnce()).release();
    }

    @Test
    void stopWorkers_호출시_예외없이_종료됨() {
        // given
        given(queueManager.blockAndPopTask(3)).willReturn(null);

        worker = new FlightMonitoringWorker(queueManager, taskProcessor, backpressure, SYNC_EXECUTOR, 1, 3);
        worker.startWorkers();

        // when & then
        assertDoesNotThrow(() -> {
            Thread.sleep(50);
            worker.stopWorkers();
        });
    }

    @Test
    void 경쟁컨슈머_다중워커_동일큐드레인_태스크유실없음() throws InterruptedException {
        // given: N=3 워커가 동시에 같은 pop에 진입해야만 통과하는 배리어.
        // 단일 폴러(현재 구현)에서는 스레드 1개만 pop에 진입 → latch가 0에 도달하지 못하고
        // 매 호출이 2초 await로 막혀 다운스트림 위임이 일어나지 않음 → RED.
        int workerCount = 3;
        int payloadCount = 30;

        CountDownLatch popEntry = new CountDownLatch(workerCount);
        ConcurrentLinkedQueue<String> payloads = new ConcurrentLinkedQueue<>();
        Set<String> dispensed = new HashSet<>();
        for (int i = 0; i < payloadCount; i++) {
            String p = "task-" + i;
            payloads.add(p);
            dispensed.add(p);
        }

        given(queueManager.blockAndPopTask(anyLong())).willAnswer(invocation -> {
            // 서로 다른 3개 스레드가 동시에 진입해야만 배리어가 풀림 (동시 폴링 증명).
            popEntry.countDown();
            popEntry.await(2, TimeUnit.SECONDS);
            return payloads.poll(); // 소진되면 null → 폴러 idle
        });
        lenient().when(queueManager.hasPendingFallbackTask()).thenReturn(false);

        worker = new FlightMonitoringWorker(queueManager, taskProcessor, backpressure, SYNC_EXECUTOR, workerCount, 3);

        // when
        worker.startWorkers();

        // then: 유실/중복 없이 정확히 M개의 서로 다른 payload가 위임되어야 함
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        then(taskProcessor).should(timeout(2000).times(payloadCount)).processTask(captor.capture());

        Set<String> delivered = new HashSet<>(captor.getAllValues());
        assertEquals(payloadCount, captor.getAllValues().size(), "위임 횟수가 payload 수와 일치해야 함 (중복 없음)");
        assertEquals(dispensed, delivered, "위임된 payload 집합이 발행 집합과 정확히 일치해야 함 (유실 없음)");
    }

    @Test
    void 경쟁컨슈머_종료신호시_모든워커_정상종료() throws InterruptedException {
        // given: permit 0인 실제 백프레셔 → 모든 폴러는 acquire() 안에서 영구 블로킹된다.
        int workerCount = 3;
        InFlightBackpressure zeroPermitBackpressure = new InFlightBackpressure(0);

        // queueManager/taskProcessor는 acquire()를 넘지 못하므로 절대 호출되지 않음 → 스텁하지 않음(불필요 스텁 방지).
        worker = new FlightMonitoringWorker(queueManager, taskProcessor, zeroPermitBackpressure, SYNC_EXECUTOR, workerCount, 3);

        // 이전 테스트에서 아직 소멸 중일 수 있는 잔존 폴러 스레드를 식별(identity 기준)해 제외한다.
        Set<Thread> preExisting = new HashSet<>();
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.getName().startsWith("MonitorPoller-")) {
                preExisting.add(t);
            }
        }

        // when
        worker.startWorkers();

        // 이 워커가 생성한 폴러 스레드 3개가 실제로 뜰 때까지 대기 후 캡처 (최대 2초)
        Set<Thread> pollers = new HashSet<>();
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            pollers.clear();
            for (Thread t : Thread.getAllStackTraces().keySet()) {
                if (t.isAlive() && t.getName().startsWith("MonitorPoller-") && !preExisting.contains(t)) {
                    pollers.add(t);
                }
            }
            if (pollers.size() >= workerCount) {
                break;
            }
            Thread.sleep(20);
        }
        assertEquals(workerCount, pollers.size(), "폴러 스레드 3개가 생성되어 캡처되어야 함(sanity)");

        // when: 종료 신호
        worker.stopWorkers();

        // then: acquire() 블로킹 상태의 모든 폴러가 인터럽트에 반응해 실제로 종료되어야 한다
        for (Thread t : pollers) {
            t.join(1000);
            assertFalse(t.isAlive(), "종료 신호 후 폴러 스레드가 실제로 종료되어야 함: " + t.getName());
        }
    }
}
