package com.siwol025.flight_monitor.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.siwol025.flight_monitor.notification.dto.EmailSendTaskDto;
import java.util.Map;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
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

    @Test
    void 카프카역직렬화_이동된알림DTO_신뢰패키지에서_정상역직렬화() {
        KafkaConfig kafkaConfig = new KafkaConfig();
        ReflectionTestUtils.setField(kafkaConfig, "bootstrapServers", "localhost:9092");
        Map<String, Object> props = kafkaConfig.consumerFactory().getConfigurationProperties();

        // 발행 측과 같이 타입 헤더(__TypeId__ = 클래스 FQN)를 붙여 직렬화한다
        EmailSendTaskDto task = new EmailSendTaskDto("user@test.com", "가격 하락", "본문");
        Headers headers = new RecordHeaders();
        byte[] payload = new JacksonJsonSerializer<>().serialize("email-send-tasks", headers, task);

        ErrorHandlingDeserializer<Object> deserializer = new ErrorHandlingDeserializer<>();
        deserializer.configure(props, false);

        assertThat(deserializer.deserialize("email-send-tasks", headers, payload))
                .as("신뢰 패키지 밖의 타입이면 ErrorHandlingDeserializer가 null을 돌려준다")
                .isEqualTo(task);
    }
}
