package com.finovago.p2p.exception;

public class LedgerEntryNotFoundException extends RuntimeException {
    public LedgerEntryNotFoundException(String message) {
        super(message);
    }
}
