package com.siwol025.flight_monitor.monitor.producer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siwol025.flight_monitor.monitor.utils.TaskQueueManager;
import com.siwol025.flight_monitor.subscription.dto.FlightMonitorTaskDto;
import com.siwol025.flight_monitor.subscription.domain.flight.SeatGrade;
import com.siwol025.flight_monitor.subscription.service.SubscriptionService;
import java.time.Duration;
import java.util.List;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * 리더 선출(Leader Election) 단일 실행 불변식 검증 통합 슬라이스 테스트.
 *
 * <p>목적: {@code @SchedulerLock} 은 AOP 로 짜여지므로, ShedLock 의 post-processor
 * ({@code @EnableSchedulerLock}, 여기서는 {@link com.siwol025.flight_monitor.global.config.ShedLockConfig}
 * 를 import 하여 활성화) 가 있는 Spring 컨텍스트에서 <b>프록시된</b> 빈을 통해 호출될 때만 락이 동작한다.
 * 따라서 {@code new FlightMonitoringProducer(...)} 직접 호출로는 검증할 수 없어, 실제 컨텍스트에서 프록시 빈을 얻어 호출한다.
 *
 * <p>불변식: 같은 주기에 여러 노드가 동시에 스케줄 메서드를 발화해도 본문은 정확히 한 번만 실행된다.
 * ShedLock 은 {@code LockProvider.lock(config)} 를 호출하여, 락을 얻으면 본문 실행, {@code Optional.empty()}
 * 이면 본문을 건너뛴다. 이를 {@link LockProvider} Mockito mock 으로 결정론적으로 재현한다.
 *
 * <p><b>리더사망_lockAtMostFor경과후_복구 시나리오에 대하여:</b>
 * 리더 노드가 죽어도 {@code lockAtMostFor} 경과 후 다른 노드가 락을 획득해 복구되는 동작은
 * ShedLock 저장소 측(Redis) 키 TTL 만료로 보장된다. 이는 실제 시간 제어가 가능한 저장소 없이는
 * 단위 검증이 불가능하며, 억지스러운 시간/TTL 테스트로 만들면 취약해진다. 대신 (1) {@code lockAtMostFor}
 * 설정값(PT300S)이 placeholder 해석을 거쳐 실제 {@link LockConfiguration} 으로 ShedLock 에 전달되는지를
 * 아래 {@code 프로듀서_리더선출_락설정_전달됨} 에서 검증하고, (2) TTL 만료 기반 복구는 ShedLock 라이브러리 자체
 * 보증에 위임한다.
 */
@SpringJUnitConfig
@TestPropertySource(properties = {
        "monitor.produce.fixed-rate-ms=300000",
        "monitor.produce.lock-at-least-for=PT290S",
        "monitor.produce.lock-at-most-for=PT300S"
})
class FlightMonitoringProducerSingleExecutionTest {

    /**
     * ShedLockConfig 를 import 하여 프로덕션의 {@code @EnableSchedulerLock} AOP post-processor 를 활성화하고,
     * 프록시 대상인 FlightMonitoringProducer 를 등록한다. LockProvider 는 @MockitoBean 으로 대체되어
     * ShedLockConfig 의 RedisLockProvider @Bean 정의를 덮어쓰므로 RedisConnectionFactory 는 필요 없다.
     */
    @Configuration
    @Import({com.siwol025.flight_monitor.global.config.ShedLockConfig.class, FlightMonitoringProducer.class})
    static class TestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @MockitoBean
    private LockProvider lockProvider;

    @MockitoBean
    private SubscriptionService subscriptionService;

    @MockitoBean
    private TaskQueueManager taskQueueManager;

    @Autowired
    private FlightMonitoringProducer producer;

    @Test
    void 프로듀서_다중노드_동시발화_주기당_1회만_발행() {
        // GIVEN: 본문이 실행되면 발행하도록 큐 크기는 임계치 미만, 활성 항공편은 비어있지 않게
        given(taskQueueManager.getQueueSizeSafely()).willReturn(0L);
        given(subscriptionService.getActiveFlights())
                .willReturn(List.of(new FlightMonitorTaskDto(1L, SeatGrade.ECONOMY)));

        // 첫 노드는 락 획득(SimpleLock 반환), 두 번째 노드는 획득 실패(Optional.empty)
        SimpleLock heldLock = mock(SimpleLock.class);
        given(lockProvider.lock(any(LockConfiguration.class)))
                .willReturn(java.util.Optional.of(heldLock))
                .willReturn(java.util.Optional.empty());

        // WHEN: 같은 주기에 두 노드가 동시에 발화한 것처럼 프록시 빈을 두 번 호출
        producer.produceMonitoringTasks();
        producer.produceMonitoringTasks();

        // THEN: 본문은 정확히 한 번만 실행되어야 한다 (두 번째 호출은 락 미획득으로 skip)
        verify(subscriptionService, times(1)).getActiveFlights();
        verify(taskQueueManager, times(1)).publishTasks(any());
    }

    @Test
    void 프로듀서_리더선출_락설정_전달됨() {
        given(taskQueueManager.getQueueSizeSafely()).willReturn(0L);
        given(subscriptionService.getActiveFlights()).willReturn(List.of());

        given(lockProvider.lock(any(LockConfiguration.class)))
                .willReturn(java.util.Optional.of(mock(SimpleLock.class)));

        // WHEN
        producer.produceMonitoringTasks();

        // THEN: placeholder 해석된 lockAtLeastFor/lockAtMostFor 와 안정적 락 이름이 ShedLock 에 전달됨
        ArgumentCaptor<LockConfiguration> captor = ArgumentCaptor.forClass(LockConfiguration.class);
        verify(lockProvider).lock(captor.capture());

        LockConfiguration config = captor.getValue();
        assertThat(config.getName()).isEqualTo("produceMonitoringTasks");
        assertThat(config.getLockAtLeastFor())
                .as("lockAtLeastFor placeholder(PT290S)가 해석되어 전달되어야 한다")
                .isEqualTo(Duration.ofSeconds(290));
        assertThat(config.getLockAtMostFor())
                .as("lockAtMostFor placeholder(PT300S)가 해석되어 전달되어야 한다")
                .isEqualTo(Duration.ofSeconds(300));
    }
}
