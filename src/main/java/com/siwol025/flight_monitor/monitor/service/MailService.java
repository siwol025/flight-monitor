package com.siwol025.flight_monitor.monitor.service;

import com.siwol025.flight_monitor.global.exception.ErrorTag;
import com.siwol025.flight_monitor.global.exception.custom.BadRequestException;
import com.siwol025.flight_monitor.monitor.dto.EmailSendTaskDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Slf4j
@Service
@Profile("prod")
@RequiredArgsConstructor
public class MailService implements NotificationService{

    private static final String EMAIL_REGEX = "[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}";
    private static final Pattern EMAIL_PATTERN = Pattern.compile(EMAIL_REGEX);

    private final JavaMailSender mailSender;

    public void sendPriceDropNotification(EmailSendTaskDto taskDto) {
        if (!EMAIL_PATTERN.matcher(taskDto.toEmail()).matches()) {
            throw new BadRequestException(ErrorTag.INVALID_INPUT);
        }

        SimpleMailMessage message = new SimpleMailMessage();

        message.setTo(taskDto.toEmail());
        message.setSubject(stripHtml(taskDto.subject()));
        message.setText(stripHtml(taskDto.content()));

        log.info("📧 [MailService] 메일 발송 중: To={}", taskDto.toEmail());
        mailSender.send(message);
        log.info("📧 [MailService] 메일 발송 완료!");
    }

    private String stripHtml(String text) {
        return text.replaceAll("<[^>]*>", "");
    }
}
