package com.siwol025.flight_monitor.monitor.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;

    public void sendPriceDropEmail(String to, String content) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("✈️ 항공권 가격 하락 알림!");
        message.setText(content);

        log.info("📧 [MailService] 메일 발송 중: To={}", to);
        mailSender.send(message);
        log.info("📧 [MailService] 메일 발송 완료!");
    }
}
