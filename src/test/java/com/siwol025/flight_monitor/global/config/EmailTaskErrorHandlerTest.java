package com.siwol025.flight_monitor.global.config;

import com.siwol025.flight_monitor.global.exception.ErrorTag;
import com.siwol025.flight_monitor.global.exception.custom.BadRequestException;
import com.siwol025.flight_monitor.notification.dto.EmailSendTaskDto;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.SendResult;
import org.springframework.core.NestedRuntimeException;
import org.springframework.mail.MailSendException;
import org.springframework.util.backoff.BackOffExecution;
import org.springframework.util.backoff.FixedBackOff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailTaskErrorHandlerTest {

    private static final String TOPIC = "email-send-tasks";
    private static final int MAX_RETRIES = 3;

    @Mock
    private KafkaOperations<String, Object> kafkaOperations;
    @Mock
    private Consumer<?, ?> consumer;
    @Mock
    private MessageListenerContainer container;

    private DefaultErrorHandler errorHandler;

    // 원본 파티션 번호(149)는 DLT 파티션 수보다 크다. 같은 번호로 보내면 발행이 실패한다.
    private final ConsumerRecord<String, Object> record =
            new ConsumerRecord<>(TOPIC, 149, 10L, "key", new EmailSendTaskDto("user@test.com", "제목", "내용"));

    @BeforeEach
    void setUp() {
        lenient().when(kafkaOperations.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(new SendResult<>(null, null)));
        lenient().when(container.isRunning()).thenReturn(true);
        KafkaConfig kafkaConfig = new KafkaConfig();
        errorHandler = kafkaConfig.emailTaskErrorHandler(kafkaOperations, new FixedBackOff(0L, MAX_RETRIES));
    }

    @Test
    void errorHandler_영구오류_재시도없이_DLT로_복구됨() {
        boolean retried = handle(new BadRequestException(ErrorTag.INVALID_INPUT));

        assertThat(retried).isFalse();

        ProducerRecord<?, ?> sent = captureSentRecord();
        assertThat(sent.topic()).isEqualTo(TOPIC + ".DLT");
    }

    @Test
    void errorHandler_일시오류_재시도횟수소진전에는_DLT로_보내지않음() {
        for (int i = 0; i < MAX_RETRIES; i++) {
            assertThat(handle(new MailSendException("SMTP 장애"))).isTrue();
        }

        verify(kafkaOperations, never()).send(any(ProducerRecord.class));
    }

    @Test
    void errorHandler_일시오류_재시도소진시_DLT로_복구됨() {
        for (int i = 0; i < MAX_RETRIES; i++) {
            handle(new MailSendException("SMTP 장애"));
        }
        boolean retried = handle(new MailSendException("SMTP 장애"));

        assertThat(retried).isFalse();

        ProducerRecord<?, ?> sent = captureSentRecord();
        assertThat(sent.topic()).isEqualTo(TOPIC + ".DLT");
    }

    @Test
    void dlt_원본파티션번호와_무관하게_발행됨() {
        handle(new BadRequestException(ErrorTag.INVALID_INPUT));

        // partition이 null이면 프로듀서가 DLT 파티션을 고른다
        assertThat(captureSentRecord().partition()).isNull();
    }

    @Test
    void emailTaskBackOff_1초부터_2배씩_3회후_중단() {
        BackOffExecution execution = KafkaConfig.emailTaskBackOff(3, 1_000L).start();

        assertThat(List.of(execution.nextBackOff(), execution.nextBackOff(), execution.nextBackOff()))
                .containsExactly(1000L, 2000L, 4000L);
        assertThat(execution.nextBackOff()).isEqualTo(BackOffExecution.STOP);
    }

    @Test
    void emailTaskBackOff_재시도5회면_16초까지_2배씩_증가후_중단() {
        BackOffExecution execution = KafkaConfig.emailTaskBackOff(5, 1_000L).start();

        assertThat(List.of(execution.nextBackOff(), execution.nextBackOff(), execution.nextBackOff(),
                execution.nextBackOff(), execution.nextBackOff()))
                .containsExactly(1000L, 2000L, 4000L, 8000L, 16000L);
        assertThat(execution.nextBackOff()).isEqualTo(BackOffExecution.STOP);
    }

    /**
     * @return true면 재시도 예정(seek 후 RecordInRetryException 신호), false면 복구(DLT) 완료
     */
    private boolean handle(RuntimeException cause) {
        // 컨테이너는 리스너 예외를 ListenerExecutionFailedException으로 감싸서 에러 핸들러에 넘긴다
        try {
            errorHandler.handleRemaining(new ListenerExecutionFailedException("listener failed", cause),
                    List.of(record), consumer, container);
            return false;
        } catch (NestedRuntimeException e) {
            // RecordInRetryException(패키지 전용): seek 후 재전달 예정이라는 신호
            assertThat(e.getClass().getSimpleName()).isEqualTo("RecordInRetryException");
            return true;
        }
    }

    private ProducerRecord<?, ?> captureSentRecord() {
        ArgumentCaptor<ProducerRecord> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaOperations, times(1)).send(captor.capture());
        return captor.getValue();
    }
}
