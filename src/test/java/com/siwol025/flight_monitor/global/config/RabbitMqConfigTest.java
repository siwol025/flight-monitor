package com.siwol025.flight_monitor.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.util.backoff.BackOffExecution;

class RabbitMqConfigTest {

    @Test
    void emailTaskBackOff_1초부터_2배씩_3회후_중단() {
        BackOffExecution execution = RabbitMqConfig.retryPolicy(3, 1_000L).getBackOff().start();

        assertThat(List.of(execution.nextBackOff(), execution.nextBackOff(), execution.nextBackOff()))
                .containsExactly(1000L, 2000L, 4000L);
        assertThat(execution.nextBackOff()).isEqualTo(BackOffExecution.STOP);
    }

    @Test
    void emailTaskBackOff_재시도5회면_16초까지_2배씩_증가후_중단() {
        BackOffExecution execution = RabbitMqConfig.retryPolicy(5, 1_000L).getBackOff().start();

        assertThat(List.of(execution.nextBackOff(), execution.nextBackOff(), execution.nextBackOff(),
                execution.nextBackOff(), execution.nextBackOff()))
                .containsExactly(1000L, 2000L, 4000L, 8000L, 16000L);
        assertThat(execution.nextBackOff()).isEqualTo(BackOffExecution.STOP);
    }
}
