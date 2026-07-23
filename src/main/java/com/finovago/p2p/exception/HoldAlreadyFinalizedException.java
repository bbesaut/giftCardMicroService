package com.finovago.p2p.exception;

public class HoldAlreadyFinalizedException extends RuntimeException {
    public HoldAlreadyFinalizedException(String message) {
        super(message);
    }
}
