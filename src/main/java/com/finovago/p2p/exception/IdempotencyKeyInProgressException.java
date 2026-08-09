package com.finovago.p2p.exception;

public class IdempotencyKeyInProgressException extends RuntimeException {
    public IdempotencyKeyInProgressException(String message) {
        super(message);
    }
}
