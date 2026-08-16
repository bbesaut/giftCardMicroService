package com.finovago.p2p.exception;

public class ServiceAccountNotAllowedException extends RuntimeException {
    public ServiceAccountNotAllowedException(String message) {
        super(message);
    }
}
