package com.finovago.p2p.exception;

public class InsufficientAvailableBalanceException extends RuntimeException {
    public InsufficientAvailableBalanceException(String message) {
        super(message);
    }
}
