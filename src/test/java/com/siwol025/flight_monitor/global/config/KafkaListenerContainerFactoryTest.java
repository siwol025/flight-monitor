package com.siwol025.flight_monitor.global.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaListenerContainerFactoryTest {

    private final KafkaConfig kafkaConfig = new KafkaConfig();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(kafkaConfig, "bootstrapServers", "localhost:9092");
    }

    @Test
    void kafkaListenerContainerFactory_fanout은_배치커밋유지() {
        var factory = kafkaConfig.kafkaListenerContainerFactory();

        assertThat(factory.getContainerProperties().getAckMode()).isEqualTo(AckMode.BATCH);
    }
}
