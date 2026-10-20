package com.siwol025.flight_monitor.monitor.worker;

import com.siwol025.flight_monitor.monitor.dto.EmailSendTaskDto;
import com.siwol025.flight_monitor.monitor.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class KafkaEmailTaskListenerTest {

    private static final String TOPIC = "email-send-tasks";

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private KafkaEmailTaskListener listener;

    private final EmailSendTaskDto dto = new EmailSendTaskDto("user@test.com", "제목", "내용");

    @Test
    void processEmailTask_발송실패시_예외전파됨() {
        doThrow(new MailSendException("SMTP 장애")).when(notificationService).sendPriceDropNotification(dto);

        // 예외를 삼키면 offset이 커밋되어 재시도·DLT 없이 유실된다. 컨테이너 에러 핸들러까지 올라가야 한다.
        assertThrows(MailSendException.class,
                () -> listener.processEmailTask(dto, TOPIC, 3, 10L, System.currentTimeMillis()));
    }

    @Test
    void processEmailTask_발송성공시_레코드키와_발행후경과시간을_로그로남김(CapturedOutput output) {
        long publishedAt = System.currentTimeMillis() - 1_500;

        listener.processEmailTask(dto, TOPIC, 3, 10L, publishedAt);

        // 실패 로그(EmailTaskErrorHandler)와 같은 "토픽-파티션@오프셋" 키로 정상 메일을 구분한다
        Matcher matcher = Pattern.compile("email-send-tasks-3@10 latency=(\\d+)ms").matcher(output.getOut());
        assertThat(matcher.find()).isTrue();
        assertThat(Long.parseLong(matcher.group(1))).isBetween(1_500L, 5_000L);
    }
}
