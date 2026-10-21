package com.siwol025.flight_monitor.global.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KafkaListenerContainerFactoryTest {

    private final KafkaConfig kafkaConfig = new KafkaConfig();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(kafkaConfig, "bootstrapServers", "localhost:9092");
    }

    @SuppressWarnings("unchecked")
    @Test
    void emailTaskListenerContainerFactory_레코드마다_offset커밋() {
        var factory = kafkaConfig.emailTaskListenerContainerFactory(mock(KafkaTemplate.class));

        assertThat(factory.getContainerProperties().getAckMode()).isEqualTo(AckMode.RECORD);
    }

    @Test
    void kafkaListenerContainerFactory_fanout은_배치커밋유지() {
        var factory = kafkaConfig.kafkaListenerContainerFactory();

        assertThat(factory.getContainerProperties().getAckMode()).isEqualTo(AckMode.BATCH);
    }
}
