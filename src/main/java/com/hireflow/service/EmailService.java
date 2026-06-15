package com.hireflow.service;

import com.hireflow.domain.Organisation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${hireflow.mail.from}")
    private String defaultFromAddress;

    public void send(String toEmail, String subject, String body) {
        send(toEmail, subject, body, null);
    }

    public void send(String toEmail, String subject, String body, Organisation org) {
        String from = (org != null && org.getMailFrom() != null && !org.getMailFrom().isBlank())
                ? org.getMailFrom()
                : defaultFromAddress;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(toEmail);
        message.setSubject(subject);
        message.setText(body);

        if (org != null && org.getMailReplyTo() != null && !org.getMailReplyTo().isBlank()) {
            message.setReplyTo(org.getMailReplyTo());
        }

        mailSender.send(message);
        log.info("Email sent to {} from {}", toEmail, from);
    }
}
