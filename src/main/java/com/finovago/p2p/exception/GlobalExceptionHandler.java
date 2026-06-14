package com.finovago.p2p.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UnknownGiftCardException.class)
    public ResponseEntity<String> handleUnknownGiftCardException(UnknownGiftCardException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(InactiveGiftCardException.class)
    public ResponseEntity<String> handleInactiveGiftCardException(InactiveGiftCardException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(ex.getMessage());
    }

}
