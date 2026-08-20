package com.finovago.p2p.exception;

public class ServiceAccountDeactivationNotAllowedException extends RuntimeException {
    public ServiceAccountDeactivationNotAllowedException(String message) {
        super(message);
    }
}
