package com.finovago.p2p.unit;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;

import io.jsonwebtoken.ExpiredJwtException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.finovago.p2p.exception.ExpiredGiftCardException;
import com.finovago.p2p.exception.GlobalExceptionHandler;
import com.finovago.p2p.exception.HoldAlreadyFinalizedException;
import com.finovago.p2p.exception.HoldNotFoundException;
import com.finovago.p2p.exception.IdempotencyKeyConflictException;
import com.finovago.p2p.exception.IdempotencyKeyInProgressException;
import com.finovago.p2p.exception.InactiveGiftCardException;
import com.finovago.p2p.exception.InsufficientAvailableBalanceException;
import com.finovago.p2p.exception.InvalidRefreshTokenException;
import com.finovago.p2p.exception.MerchantNotFoundException;
import com.finovago.p2p.exception.UnknownGiftCardException;
import com.finovago.p2p.exception.UserAlreadyExistsException;

class GlobalExceptionHandlerUnitTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static void assertErrorBody(ResponseEntity<?> response, HttpStatus status, String error, String message) {
        assertEquals(status, response.getStatusCode());
        assertEquals(Map.of("error", error, "message", message), response.getBody());
    }

    @Test
    void handleUnknownGiftCardException_returns404() {
        ResponseEntity<Object> response = handler.handleUnknownGiftCardException(new UnknownGiftCardException("card not found"));
        assertErrorBody(response, HttpStatus.NOT_FOUND, "Not Found", "card not found");
    }

    @Test
    void handleInactiveGiftCardException_returns422() {
        ResponseEntity<Object> response = handler.handleInactiveGiftCardException(new InactiveGiftCardException("card inactive"));
        assertErrorBody(response, HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable Entity", "card inactive");
    }

    @Test
    void handleExpiredGiftCardException_returns422() {
        ResponseEntity<Object> response = handler.handleExpiredGiftCardException(new ExpiredGiftCardException("card expired"));
        assertErrorBody(response, HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable Entity", "card expired");
    }

    @Test
    void handleHoldNotFoundException_returns404() {
        ResponseEntity<Object> response = handler.handleHoldNotFoundException(new HoldNotFoundException("hold not found"));
        assertErrorBody(response, HttpStatus.NOT_FOUND, "Not Found", "hold not found");
    }

    @Test
    void handleHoldAlreadyFinalizedException_returns409() {
        ResponseEntity<Object> response = handler.handleHoldAlreadyFinalizedException(new HoldAlreadyFinalizedException("hold already finalized"));
        assertErrorBody(response, HttpStatus.CONFLICT, "Conflict", "hold already finalized");
    }

    @Test
    void handleInsufficientAvailableBalanceException_returns422() {
        ResponseEntity<Object> response = handler.handleInsufficientAvailableBalanceException(new InsufficientAvailableBalanceException("insufficient balance"));
        assertErrorBody(response, HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable Entity", "insufficient balance");
    }

    @Test
    void handleIdempotencyKeyConflictException_returns409() {
        ResponseEntity<Object> response = handler.handleIdempotencyKeyConflictException(new IdempotencyKeyConflictException("key conflict"));
        assertErrorBody(response, HttpStatus.CONFLICT, "Conflict", "key conflict");
    }

    @Test
    void handleIdempotencyKeyInProgressException_returns409() {
        ResponseEntity<Object> response = handler.handleIdempotencyKeyInProgressException(new IdempotencyKeyInProgressException("key in progress"));
        assertErrorBody(response, HttpStatus.CONFLICT, "Conflict", "key in progress");
    }

    @Test
    void handleMissingRequestHeaderException_returns400WithHeaderNameInMessage() {
        MissingRequestHeaderException ex = mock(MissingRequestHeaderException.class);
        when(ex.getHeaderName()).thenReturn("Idempotency-Key");

        ResponseEntity<Object> response = handler.handleMissingRequestHeaderException(ex);

        assertErrorBody(response, HttpStatus.BAD_REQUEST, "Bad Request", "The Idempotency-Key header is required");
    }

    @Test
    void handleValidationExceptions_returns400WithFirstFieldErrorMessage() {
        FieldError fieldError = new FieldError("giftCardCreateRequest", "balance", "must be positive");
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(mock(MethodParameter.class), bindingResult);

        ResponseEntity<Object> response = handler.handleValidationExceptions(ex);

        assertErrorBody(response, HttpStatus.BAD_REQUEST, "Bad Request", "must be positive");
    }

    @Test
    void handleExpiredJwt_returns401() {
        ResponseEntity<Map<String, Object>> response = handler.handleExpiredJwt(mock(ExpiredJwtException.class));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(Map.of(
                "status", 401,
                "error", "Unauthorized",
                "message", "Token has expired. Please log in again to obtain a new token."
        ), response.getBody());
    }

    @Test
    void handleInvalidJwt_returns401() {
        ResponseEntity<Map<String, Object>> response = handler.handleInvalidJwt();

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(Map.of(
                "status", 401,
                "error", "Unauthorized",
                "message", "Invalid token, altered or corrupted."
        ), response.getBody());
    }

    @Test
    void handleInvalidRefreshTokenException_returns401() {
        ResponseEntity<Object> response = handler.handleInvalidRefreshTokenException(new InvalidRefreshTokenException("token expired"));
        assertErrorBody(response, HttpStatus.UNAUTHORIZED, "Unauthorized", "token expired");
    }

    @Test
    void handleIllegalArgumentException_returns409() {
        ResponseEntity<Object> response = handler.handleIllegalArgumentException(new IllegalArgumentException("bad amount"));
        assertErrorBody(response, HttpStatus.CONFLICT, "Conflict", "bad amount");
    }

    @Test
    void handleMerchantNotFoundException_returns404() {
        ResponseEntity<Object> response = handler.handleMerchantNotFoundException(new MerchantNotFoundException("merchant not found"));
        assertErrorBody(response, HttpStatus.NOT_FOUND, "Not Found", "merchant not found");
    }

    @Test
    void handleUserAlreadyExistsException_returns409() {
        ResponseEntity<Object> response = handler.handleUserAlreadyExistsException(new UserAlreadyExistsException("email already registered"));
        assertErrorBody(response, HttpStatus.CONFLICT, "Conflict", "email already registered");
    }

    @Test
    void handleBadCredentialsException_returns401WithGenericMessage() {
        ResponseEntity<Object> response = handler.handleBadCredentialsException(new BadCredentialsException("wrong password"));
        assertErrorBody(response, HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid email or password");
    }
}
