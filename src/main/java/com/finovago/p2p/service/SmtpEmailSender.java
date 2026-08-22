package com.finovago.p2p.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * SMTP-based sender - used everywhere except prod. Works fine in dev (Brevo's SMTP relay from a
 * regular machine/network) and in tests (MailHog), but most PaaS hosts (Render included) block
 * outbound SMTP ports at the network level regardless of credentials - see BrevoApiEmailSender for
 * the prod path.
 */
@Slf4j
@Service
@Profile("!prod")
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpEmailSender(JavaMailSender mailSender, @Value("${app.mail.from}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
        log.debug("Email sent to {} with subject: {}", to, subject);
    }
}
