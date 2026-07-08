package com.finovago.p2p.exception;

import java.util.Map;

import org.jboss.logging.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

   @ExceptionHandler(UnknownGiftCardException.class)
    public ResponseEntity<Object> handleUnknownGiftCardException(UnknownGiftCardException ex) {
        log.warn(ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                    "error", "Not Found",
                    "message", ex.getMessage(), 
                    "code", MDC.get("correlationId")
                ));
    }

    @ExceptionHandler(InactiveGiftCardException.class)
    public ResponseEntity<Object> handleInactiveGiftCardException(InactiveGiftCardException ex) {
        log.warn(ex.getMessage()); // log the throw message

        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of(
                    "error", "Unprocessable Entity",
                    "message", ex.getMessage(),
                    "code", MDC.get("correlationId")
                ));
    }

    @ExceptionHandler(ExpiredGiftCardException.class)
    public ResponseEntity<Object> handleExpiredGiftCardException(ExpiredGiftCardException ex) {
        log.warn(ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of(
                    "error", "Unprocessable Entity",
                    "message", ex.getMessage(),
                    "code", MDC.get("correlationId")
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidationExceptions(MethodArgumentNotValidException ex) { // when a request body fails vaidation with @Valid
        String errorMessage = ex.getBindingResult() 
                .getFieldErrors()
                .get(0)
                .getDefaultMessage();

        log.warn("Invalid request blocked by validation : {}", errorMessage);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST) 
                .body(Map.of(
                    "error", "Bad Request",
                    "message", errorMessage,
                    "code", MDC.get("correlationId")
                ));
    }

    @ExceptionHandler(ExpiredJwtException.class)
    public ResponseEntity<Map<String, Object>> handleExpiredJwt(ExpiredJwtException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(
                    "status", 401,
                    "error", "Unauthorized",
                    "message", "Token has expired. Please log in again to obtain a new token."
                ));
    }

    @ExceptionHandler({MalformedJwtException.class, SignatureException.class})
    public ResponseEntity<Map<String, Object>> handleInvalidJwt() {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(
                    "status", 401,
                    "error", "Unauthorized",
                    "message", "Invalid token, altered or corrupted."
                ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgumentException(IllegalArgumentException ex) {
    
    log.warn("Illegal argument provided: {}", ex.getMessage());

    return ResponseEntity
            .status(HttpStatus.CONFLICT) 
            .body(Map.of(
                "error", "Conflict",
                "message", ex.getMessage(), 
                "code", MDC.get("correlationId")
            ));
}

}
