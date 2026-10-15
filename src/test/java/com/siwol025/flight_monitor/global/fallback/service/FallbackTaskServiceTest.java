package com.siwol025.flight_monitor.global.fallback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.siwol025.flight_monitor.global.fallback.domain.FallbackMonitoringTask;
import com.siwol025.flight_monitor.global.fallback.domain.FallbackMonitoringTask.TaskStatus;
import com.siwol025.flight_monitor.global.fallback.repository.FallbackMonitoringTaskRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FallbackTaskServiceTest {

    @Mock
    private FallbackMonitoringTaskRepository fallbackRepository;

    @InjectMocks
    private FallbackTaskService fallbackTaskService;

    @Test
    @SuppressWarnings("unchecked")
    void savePendingTasks_페이로드별_PENDING태스크_일괄저장됨() {
        // given
        List<String> payloads = List.of("payload-1", "payload-2");

        // when
        fallbackTaskService.savePendingTasks(payloads);

        // then
        ArgumentCaptor<List<FallbackMonitoringTask>> captor = ArgumentCaptor.forClass(List.class);
        verify(fallbackRepository).saveAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(FallbackMonitoringTask::getPayload, FallbackMonitoringTask::getStatus)
                .containsExactly(
                        tuple("payload-1", TaskStatus.PENDING),
                        tuple("payload-2", TaskStatus.PENDING));
    }

    @Test
    void countTasks_DB폴백_적재건수_반환() {
        // given
        given(fallbackRepository.count()).willReturn(42L);

        // when & then
        assertThat(fallbackTaskService.countTasks()).isEqualTo(42L);
    }
}
