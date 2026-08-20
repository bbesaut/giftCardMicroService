package com.finovago.p2p.exception;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
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
                    "message", ex.getMessage()
                ));
    }

    @ExceptionHandler(InactiveGiftCardException.class)
    public ResponseEntity<Object> handleInactiveGiftCardException(InactiveGiftCardException ex) {
        log.warn(ex.getMessage()); // log the throw message

        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of(
                    "error", "Unprocessable Entity",
                    "message", ex.getMessage()
                ));
    }

    @ExceptionHandler(ExpiredGiftCardException.class)
    public ResponseEntity<Object> handleExpiredGiftCardException(ExpiredGiftCardException ex) {
        log.warn(ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of(
                    "error", "Unprocessable Entity",
                    "message", ex.getMessage()
                ));
    }

    @ExceptionHandler(HoldNotFoundException.class)
    public ResponseEntity<Object> handleHoldNotFoundException(HoldNotFoundException ex) {
        log.warn(ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                    "error", "Not Found",
                    "message", ex.getMessage()
                ));
    }

    @ExceptionHandler(HoldAlreadyFinalizedException.class)
    public ResponseEntity<Object> handleHoldAlreadyFinalizedException(HoldAlreadyFinalizedException ex) {
        log.warn(ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of(
                    "error", "Conflict",
                    "message", ex.getMessage()
                ));
    }

    @ExceptionHandler(InsufficientAvailableBalanceException.class)
    public ResponseEntity<Object> handleInsufficientAvailableBalanceException(InsufficientAvailableBalanceException ex) {
        log.warn(ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of(
                    "error", "Unprocessable Entity",
                    "message", ex.getMessage()
                ));
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public ResponseEntity<Object> handleIdempotencyKeyConflictException(IdempotencyKeyConflictException ex) {
        log.warn(ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of(
                    "error", "Conflict",
                    "message", ex.getMessage()
                ));
    }

    @ExceptionHandler(IdempotencyKeyInProgressException.class)
    public ResponseEntity<Object> handleIdempotencyKeyInProgressException(IdempotencyKeyInProgressException ex) {
        log.warn(ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of(
                    "error", "Conflict",
                    "message", ex.getMessage()
                ));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Object> handleMissingRequestHeaderException(MissingRequestHeaderException ex) {
        log.warn("Missing required header: {}", ex.getHeaderName());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                    "error", "Bad Request",
                    "message", "The " + ex.getHeaderName() + " header is required"
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
                    "message", errorMessage
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

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<Object> handleInvalidRefreshTokenException(InvalidRefreshTokenException ex) {
        log.warn(ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(
                    "error", "Unauthorized",
                    "message", ex.getMessage()
                ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgumentException(IllegalArgumentException ex) {

    log.warn("Illegal argument provided: {}", ex.getMessage());

    return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(Map.of(
                "error", "Conflict",
                "message", ex.getMessage()
            ));
}

    @ExceptionHandler(LedgerEntryNotFoundException.class)
    public ResponseEntity<Object> handleLedgerEntryNotFoundException(LedgerEntryNotFoundException ex) {
        log.warn(ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                    "error", "Not Found",
                    "message", ex.getMessage()
                ));
    }

    @ExceptionHandler(InvalidRefundTargetException.class)
    public ResponseEntity<Object> handleInvalidRefundTargetException(InvalidRefundTargetException ex) {
        log.warn(ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of(
                    "error", "Unprocessable Entity",
                    "message", ex.getMessage()
                ));
    }

    @ExceptionHandler(RefundExceedsOriginalAmountException.class)
    public ResponseEntity<Object> handleRefundExceedsOriginalAmountException(RefundExceedsOriginalAmountException ex) {
        log.warn(ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of(
                    "error", "Unprocessable Entity",
                    "message", ex.getMessage()
                ));
    }

    @ExceptionHandler(ServiceAccountNotAllowedException.class)
    public ResponseEntity<Object> handleServiceAccountNotAllowedException(ServiceAccountNotAllowedException ex) {
        log.warn(ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                    "error", "Forbidden",
                    "message", ex.getMessage()
                ));
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<Object> handleUserAlreadyExistsException(UserAlreadyExistsException ex) {
        log.warn(ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of(
                    "error", "Conflict",
                    "message", ex.getMessage()
                ));
    }

    @ExceptionHandler(OwnerPrivilegeRequiredException.class)
    public ResponseEntity<Object> handleOwnerPrivilegeRequiredException(OwnerPrivilegeRequiredException ex) {
        log.warn(ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                    "error", "Forbidden",
                    "message", ex.getMessage()
                ));
    }

    @ExceptionHandler(SelfDeactivationException.class)
    public ResponseEntity<Object> handleSelfDeactivationException(SelfDeactivationException ex) {
        log.warn(ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of(
                    "error", "Conflict",
                    "message", ex.getMessage()
                ));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Object> handleUserNotFoundException(UserNotFoundException ex) {
        log.warn(ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                    "error", "Not Found",
                    "message", ex.getMessage()
                ));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Object> handleBadCredentialsException(BadCredentialsException ex) {
        log.warn("Authentication failed - invalid credentials");

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(
                    "error", "Unauthorized",
                    "message", "Invalid email or password"
                ));
    }

}
