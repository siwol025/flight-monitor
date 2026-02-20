package com.siwol025.flight_monitor.monitor.service;

import com.siwol025.flight_monitor.monitor.dto.EmailSendTaskDto;
import com.siwol025.flight_monitor.monitor.dto.PriceDropNotificationDto;
import com.siwol025.flight_monitor.subscription.service.SubscriptionService;
import com.siwol025.flight_monitor.user.dto.UserEmailDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService implements NotificationService{

    private final JavaMailSender mailSender;

    public void sendPriceDropNotification(EmailSendTaskDto taskDto) {
        SimpleMailMessage message = new SimpleMailMessage();

        message.setTo(taskDto.toEmail());
        message.setSubject(taskDto.subject());
        message.setText(taskDto.content());

        log.info("📧 [MailService] 메일 발송 중: To={}", taskDto.toEmail());
        mailSender.send(message);
        log.info("📧 [MailService] 메일 발송 완료!");
    }
}
