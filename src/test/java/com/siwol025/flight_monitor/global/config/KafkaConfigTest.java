package com.siwol025.flight_monitor.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.test.util.ReflectionTestUtils;

class KafkaConfigTest {

    @Test
    void consumerFactory_TRUSTED_PACKAGES가_와일드카드_아님() {
        KafkaConfig kafkaConfig = new KafkaConfig();
        ReflectionTestUtils.setField(kafkaConfig, "bootstrapServers", "localhost:9092");

        ConsumerFactory<String, Object> consumerFactory = kafkaConfig.consumerFactory();
        Map<String, Object> props = consumerFactory.getConfigurationProperties();

        Object trustedPackages = props.get(JacksonJsonDeserializer.TRUSTED_PACKAGES);

        assertThat(trustedPackages).isNotEqualTo("*");
    }
}
