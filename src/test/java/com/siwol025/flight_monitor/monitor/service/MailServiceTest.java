package com.siwol025.flight_monitor.monitor.service;

import com.siwol025.flight_monitor.global.exception.custom.BadRequestException;
import com.siwol025.flight_monitor.monitor.dto.EmailSendTaskDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private MailService mailService;

    @BeforeEach
    void setUp() {
        mailService = new MailService(mailSender);
    }

    @Test
    void sendPriceDropNotification_유효하지않은_이메일형식이면_BadRequestException_발생() {
        EmailSendTaskDto dto = new EmailSendTaskDto("not-an-email", "제목", "내용");

        assertThrows(BadRequestException.class, () -> mailService.sendPriceDropNotification(dto));
    }

}
