package com.finovago.p2p.unit;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.finovago.p2p.dto.RedemptionResponse;
import com.finovago.p2p.exception.IdempotencyKeyConflictException;
import com.finovago.p2p.exception.IdempotencyKeyInProgressException;
import com.finovago.p2p.model.IdempotencyKey;
import com.finovago.p2p.repository.IdempotencyKeyRepository;
import com.finovago.p2p.service.IdempotencyKeyService;

@ExtendWith(MockitoExtension.class)
class IdempotencyKeyServiceUnitTest {
    private static final Long MERCHANT_ID = 1L;
    private static final String KEY = "client-key-123";

    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;

    private IdempotencyKeyService idempotencyKeyService;

    @BeforeEach
    void setUp() {
        idempotencyKeyService = new IdempotencyKeyService(idempotencyKeyRepository, 24);
    }

    private String hash(String code, double amount) {
        return idempotencyKeyService.hashRequest(code, amount);
    }

    @Test
    void claim_proceedsWithFreshKey_whenNoExistingEntry() {
        String requestHash = hash("GC-1", 10.0);
        when(idempotencyKeyRepository.findByMerchantIdAndIdempotencyKey(MERCHANT_ID, KEY)).thenReturn(Optional.empty());

        Optional<RedemptionResponse> result = idempotencyKeyService.claim(MERCHANT_ID, KEY, requestHash);

        assertTrue(result.isEmpty());
        verify(idempotencyKeyRepository).saveAndFlush(ArgumentMatchers.any(IdempotencyKey.class));
    }

    @Test
    void claim_returnsCachedResponse_whenSameKeyAlreadyCompletedWithSameRequest() {
        String requestHash = hash("GC-1", 10.0);
        IdempotencyKey completed = new IdempotencyKey(MERCHANT_ID, KEY, requestHash, LocalDateTime.now().plusHours(1));
        completed.complete("SUCCESS", 10.0, 40.0, 0.0);
        when(idempotencyKeyRepository.findByMerchantIdAndIdempotencyKey(MERCHANT_ID, KEY)).thenReturn(Optional.of(completed));

        Optional<RedemptionResponse> result = idempotencyKeyService.claim(MERCHANT_ID, KEY, requestHash);

        assertTrue(result.isPresent());
        assertEquals(new RedemptionResponse("SUCCESS", 10.0, 40.0, 0.0), result.get());
        verify(idempotencyKeyRepository, never()).saveAndFlush(ArgumentMatchers.any());
    }

    @Test
    void claim_throwsInProgress_whenSameKeyStillBeingProcessed() {
        String requestHash = hash("GC-1", 10.0);
        IdempotencyKey inProgress = new IdempotencyKey(MERCHANT_ID, KEY, requestHash, LocalDateTime.now().plusHours(1));
        when(idempotencyKeyRepository.findByMerchantIdAndIdempotencyKey(MERCHANT_ID, KEY)).thenReturn(Optional.of(inProgress));

        assertThrows(IdempotencyKeyInProgressException.class, () -> idempotencyKeyService.claim(MERCHANT_ID, KEY, requestHash));
    }

    @Test
    void claim_throwsConflict_whenSameKeyReusedWithDifferentPayload() {
        IdempotencyKey completed = new IdempotencyKey(MERCHANT_ID, KEY, hash("GC-1", 10.0), LocalDateTime.now().plusHours(1));
        completed.complete("SUCCESS", 10.0, 40.0, 0.0);
        when(idempotencyKeyRepository.findByMerchantIdAndIdempotencyKey(MERCHANT_ID, KEY)).thenReturn(Optional.of(completed));

        assertThrows(IdempotencyKeyConflictException.class,
                () -> idempotencyKeyService.claim(MERCHANT_ID, KEY, hash("GC-1", 999.0)));
    }

    @Test
    void claim_resolvesFromConcurrentWinner_whenSaveLosesTheInsertRace() {
        String requestHash = hash("GC-1", 10.0);
        IdempotencyKey wonByConcurrentRequest = new IdempotencyKey(MERCHANT_ID, KEY, requestHash, LocalDateTime.now().plusHours(1));

        when(idempotencyKeyRepository.findByMerchantIdAndIdempotencyKey(MERCHANT_ID, KEY))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(wonByConcurrentRequest));
        when(idempotencyKeyRepository.saveAndFlush(ArgumentMatchers.any(IdempotencyKey.class)))
                .thenThrow(new DataIntegrityViolationException("unique violation"));

        assertThrows(IdempotencyKeyInProgressException.class, () -> idempotencyKeyService.claim(MERCHANT_ID, KEY, requestHash));
        verify(idempotencyKeyRepository, times(2)).findByMerchantIdAndIdempotencyKey(MERCHANT_ID, KEY);
    }

    @Test
    void complete_marksExistingClaimAsCompleted() {
        String requestHash = hash("GC-1", 10.0);
        IdempotencyKey claim = new IdempotencyKey(MERCHANT_ID, KEY, requestHash, LocalDateTime.now().plusHours(1));
        when(idempotencyKeyRepository.findByMerchantIdAndIdempotencyKey(MERCHANT_ID, KEY)).thenReturn(Optional.of(claim));

        idempotencyKeyService.complete(MERCHANT_ID, KEY, new RedemptionResponse("SUCCESS", 10.0, 40.0, 0.0));

        assertEquals(10.0, claim.getDeductedAmount());
        assertTrue(!claim.isInProgress());
    }

    @Test
    void discard_deletesExistingClaim() {
        IdempotencyKey claim = new IdempotencyKey(MERCHANT_ID, KEY, hash("GC-1", 10.0), LocalDateTime.now().plusHours(1));
        when(idempotencyKeyRepository.findByMerchantIdAndIdempotencyKey(MERCHANT_ID, KEY)).thenReturn(Optional.of(claim));

        idempotencyKeyService.discard(MERCHANT_ID, KEY);

        verify(idempotencyKeyRepository).delete(claim);
    }

    @Test
    void deleteExpired_removesOnlyExpiredEntries() {
        LocalDateTime cutoff = LocalDateTime.now();
        IdempotencyKey expired = new IdempotencyKey(MERCHANT_ID, KEY, hash("GC-1", 10.0), cutoff.minusMinutes(1));
        when(idempotencyKeyRepository.findByExpiresAtBefore(cutoff)).thenReturn(List.of(expired));

        idempotencyKeyService.deleteExpired(cutoff);

        verify(idempotencyKeyRepository).deleteAll(List.of(expired));
    }

    @Test
    void hashRequest_isDeterministic_andSensitiveToInputs() {
        assertEquals(hash("GC-1", 10.0), hash("GC-1", 10.0));
        assertNotEquals(hash("GC-1", 10.0), hash("GC-1", 20.0));
        assertNotEquals(hash("GC-1", 10.0), hash("GC-2", 10.0));
    }
}
