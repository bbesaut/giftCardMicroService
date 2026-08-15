package com.finovago.p2p.exception;

public class RefundExceedsOriginalAmountException extends RuntimeException {
    public RefundExceedsOriginalAmountException(String message) {
        super(message);
    }
}
