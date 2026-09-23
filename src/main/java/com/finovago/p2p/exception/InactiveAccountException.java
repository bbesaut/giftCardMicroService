package com.finovago.p2p.exception;

/** The authenticated account no longer exists or has been deactivated, so its (still unexpired) access token must not be honoured. */
public class InactiveAccountException extends RuntimeException {
    public InactiveAccountException(String message) {
        super(message);
    }
}
