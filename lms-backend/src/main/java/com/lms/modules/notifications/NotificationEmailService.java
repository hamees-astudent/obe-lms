package com.lms.modules.notifications;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sends plain-text notification emails asynchronously.
 * Fire-and-forget — failures are logged but not propagated.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationEmailService {

    private final JavaMailSender mailSender;

    @Value("${lms.notifications.from-email}")
    private String fromEmail;

    @Value("${lms.notifications.from-name}")
    private String fromName;

    /**
     * Send a plain-text email. Runs on the async thread pool so the Kafka
     * consumer thread is never blocked.
     *
     * @param to      recipient address
     * @param subject email subject
     * @param body    plain-text body
     */
    @Async
    public void sendEmail(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromName + " <" + fromEmail + ">");
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.debug("Email sent to {} — subject: {}", to, subject);
        } catch (MailException ex) {
            log.error("Failed to send email to {} — subject: {} — {}", to, subject, ex.getMessage());
        }
    }
}
