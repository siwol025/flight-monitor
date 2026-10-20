package com.siwol025.flight_monitor.monitor.worker;

import com.siwol025.flight_monitor.monitor.dto.EmailSendTaskDto;
import com.siwol025.flight_monitor.monitor.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class KafkaEmailTaskListenerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private KafkaEmailTaskListener listener;

    @Test
    void processEmailTask_발송실패시_예외전파됨() {
        EmailSendTaskDto dto = new EmailSendTaskDto("user@test.com", "제목", "내용");
        doThrow(new MailSendException("SMTP 장애")).when(notificationService).sendPriceDropNotification(dto);

        // 예외를 삼키면 offset이 커밋되어 재시도·DLT 없이 유실된다. 컨테이너 에러 핸들러까지 올라가야 한다.
        assertThrows(MailSendException.class, () -> listener.processEmailTask(dto));
    }
}
