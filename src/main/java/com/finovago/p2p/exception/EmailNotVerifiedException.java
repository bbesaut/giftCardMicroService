package com.finovago.p2p.exception;

/** The password was correct, but the account has not confirmed its email address yet, so no session is issued. */
public class EmailNotVerifiedException extends RuntimeException {
    public EmailNotVerifiedException(String message) {
        super(message);
    }
}
