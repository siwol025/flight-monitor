package com.siwol025.flight_monitor.notification.worker;

import com.siwol025.flight_monitor.notification.dto.EmailSendTaskDto;
import com.siwol025.flight_monitor.notification.mail.NotificationService;
import java.util.Date;
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
class EmailTaskListenerTest {

    private static final String MESSAGE_ID = "6f1c2a9e-0000-4000-8000-000000000001";

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private EmailTaskListener listener;

    private final EmailSendTaskDto dto = new EmailSendTaskDto("user@test.com", "제목", "내용");

    @Test
    void processEmailTask_발송실패시_예외전파됨() {
        doThrow(new MailSendException("SMTP 장애")).when(notificationService).sendPriceDropNotification(dto);

        // 예외를 삼키면 ack되어 재시도·DLQ 없이 유실된다. 컨테이너의 재시도까지 올라가야 한다.
        assertThrows(MailSendException.class,
                () -> listener.processEmailTask(dto, MESSAGE_ID, new Date()));
    }

    @Test
    void processEmailTask_발송성공시_메시지id와_발행후경과시간을_로그로남김(CapturedOutput output) {
        Date publishedAt = new Date(System.currentTimeMillis() - 1_500);

        listener.processEmailTask(dto, MESSAGE_ID, publishedAt);

        // 실패 로그(EmailTaskRetry)와 같은 "큐 id=메시지id" 키로 정상 메일을 구분한다
        Matcher matcher = Pattern.compile("email-send-tasks id=" + MESSAGE_ID + " latency=(\\d+)ms")
                .matcher(output.getOut());
        assertThat(matcher.find()).isTrue();
        assertThat(Long.parseLong(matcher.group(1))).isBetween(1_500L, 5_000L);
    }
}
