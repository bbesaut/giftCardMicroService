package com.finovago.p2p.exception;

public class SelfDeactivationException extends RuntimeException {
    public SelfDeactivationException(String message) {
        super(message);
    }
}
