package com.finovago.p2p.service;

/** Abstraction over how an email actually gets delivered - see SmtpEmailSender and BrevoApiEmailSender. */
public interface EmailSender {
    void send(String to, String subject, String body);
}
