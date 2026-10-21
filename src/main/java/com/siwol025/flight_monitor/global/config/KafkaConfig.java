package com.siwol025.flight_monitor.global.config;

import com.siwol025.flight_monitor.global.exception.custom.BadRequestException;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.util.backoff.BackOff;

@Slf4j
@EnableKafka
@Configuration
public class KafkaConfig {

    private static final String EMAIL_SEND_TASK_DLT_TOPIC = "email-send-tasks.DLT";

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${kafka.topic.price-drop-events.partitions:9}")
    private int priceDropPartitions;

    @Value("${kafka.topic.email-send-tasks.partitions:150}")
    private int emailSendTaskPartitions;

    @Value("${kafka.topic.email-send-tasks-dlt.partitions:3}")
    private int emailSendTaskDltPartitions;

    // 블로킹 재시도: 재시도 총 대기 = initial × (2^maxRetries − 1). 그동안 같은 파티션의 뒤 메시지가 기다린다.
    // 기본 5회(31초): 재시도 1주기가 장애 시간보다 짧으면 장애 중 컨슈머마다 메일을 DLT로 소진시킨다.
    // 30초 SMTP 장애 측정에서 3회(7초)는 DLT 150건, 5회는 0건
    @Value("${kafka.email-task.retry.max-retries:5}")
    private int emailTaskMaxRetries;

    @Value("${kafka.email-task.retry.initial-interval-ms:1000}")
    private long emailTaskInitialIntervalMs;

    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new KafkaAdmin(configs);
    }

    @Bean
    public NewTopic priceDropEventTopic() {
        return TopicBuilder.name("flight-price-drop-events")
                .partitions(priceDropPartitions)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic emailSendTaskTopic() {
        return TopicBuilder.name("email-send-tasks")
                .partitions(emailSendTaskPartitions)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic emailSendTaskDltTopic() {
        return TopicBuilder.name(EMAIL_SEND_TASK_DLT_TOPIC)
                .partitions(emailSendTaskDltPartitions)
                .replicas(1)
                .build();
    }

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);

        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JacksonJsonDeserializer.class);

        props.put(JacksonJsonDeserializer.TRUSTED_PACKAGES,
            "com.siwol025.flight_monitor.monitor.dto," +
            "com.siwol025.flight_monitor.subscription.dto");

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());

        return factory;
    }

    /**
     * 메일 발송 리스너 전용 팩토리. 공용 팩토리에 DLT 복구기를 붙이면 다른 리스너도
     * 선언되지 않은 {@code <topic>.DLT}로 발행을 시도하므로 메일 리스너에만 적용한다.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> emailTaskListenerContainerFactory(
            KafkaTemplate<String, Object> kafkaTemplate) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setCommonErrorHandler(emailTaskErrorHandler(kafkaTemplate,
                emailTaskBackOff(emailTaskMaxRetries, emailTaskInitialIntervalMs)));
        // 메일 1건이 수백 ms라 배치 커밋이면 poll 한 번 분량(수십 초)이 커밋 없이 처리된다.
        // 그 사이 재시작(배포 포함)하면 이미 보낸 메일이 재전달되므로 레코드마다 커밋한다.
        factory.getContainerProperties().setAckMode(AckMode.RECORD);

        return factory;
    }

    /**
     * 일시 오류는 backOff만큼 블로킹 재시도 후 DLT로, 영구 오류는 재시도 없이 바로 DLT로 보낸다.
     */
    DefaultErrorHandler emailTaskErrorHandler(KafkaOperations<String, Object> kafkaOperations, BackOff backOff) {
        // 원본 파티션 번호(최대 149)를 그대로 쓰면 DLT 파티션 수를 원본과 맞춰야 한다. -1이면 프로듀서가 고른다.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaOperations,
                (record, ex) -> new TopicPartition(EMAIL_SEND_TASK_DLT_TOPIC, -1));

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.addNotRetryableExceptions(BadRequestException.class);
        errorHandler.setRetryListeners(new RetryListener() {
            @Override
            public void failedDelivery(ConsumerRecord<?, ?> record, Exception ex, int deliveryAttempt) {
                log.warn("[EmailTaskErrorHandler] 발송 실패: {}-{}@{} attempt={} cause={}",
                        record.topic(), record.partition(), record.offset(), deliveryAttempt,
                        NestedExceptionUtils.getMostSpecificCause(ex).getClass().getSimpleName());
            }

            @Override
            public void recovered(ConsumerRecord<?, ?> record, Exception ex) {
                log.error("[EmailTaskErrorHandler] DLT 이동: {}-{}@{}",
                        record.topic(), record.partition(), record.offset(), ex);
            }
        });

        return errorHandler;
    }

    static BackOff emailTaskBackOff(int maxRetries, long initialIntervalMs) {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(maxRetries);
        backOff.setInitialInterval(initialIntervalMs);
        backOff.setMultiplier(2.0);
        return backOff;
    }
}
