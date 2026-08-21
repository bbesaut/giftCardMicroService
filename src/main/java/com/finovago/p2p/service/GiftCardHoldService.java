package com.finovago.p2p.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finovago.p2p.dto.HoldResponse;
import com.finovago.p2p.dto.ReserveRequest;
import com.finovago.p2p.exception.HoldAlreadyFinalizedException;
import com.finovago.p2p.exception.HoldNotFoundException;
import com.finovago.p2p.exception.InsufficientAvailableBalanceException;
import com.finovago.p2p.exception.UnknownGiftCardException;
import com.finovago.p2p.model.GiftCard;
import com.finovago.p2p.model.GiftCardHold;
import com.finovago.p2p.model.HoldStatus;
import com.finovago.p2p.model.LedgerEntryType;
import com.finovago.p2p.repository.GiftCardHoldRepository;
import com.finovago.p2p.repository.GiftCardRepository;
import com.finovago.p2p.security.CurrentUserContext;

@Service
public class GiftCardHoldService {
    private static final Logger log = LoggerFactory.getLogger(GiftCardHoldService.class);

    private final GiftCardHoldRepository giftCardHoldRepository;
    private final GiftCardRepository giftCardRepository;
    private final CurrentUserContext currentUserContext;
    private final IdempotencyKeyService idempotencyKeyService;
    private final LedgerService ledgerService;
    private final long holdTtlMinutes;

    public GiftCardHoldService(
            GiftCardHoldRepository giftCardHoldRepository,
            GiftCardRepository giftCardRepository,
            CurrentUserContext currentUserContext,
            IdempotencyKeyService idempotencyKeyService,
            LedgerService ledgerService,
            @Value("${app.holds.ttl-minutes:15}") long holdTtlMinutes) {
        this.giftCardHoldRepository = giftCardHoldRepository;
        this.giftCardRepository = giftCardRepository;
        this.currentUserContext = currentUserContext;
        this.idempotencyKeyService = idempotencyKeyService;
        this.ledgerService = ledgerService;
        this.holdTtlMinutes = holdTtlMinutes;
    }

    /**
     * claim()/complete()/discard() call a different bean (IdempotencyKeyService), so their
     * REQUIRES_NEW transactions correctly nest inside this method's transaction despite it
     * being a single @Transactional method - unlike a self-invoked inner method, this doesn't
     * risk losing the pessimistic lock held below for the whole reserve.
     */
    @Transactional
    public HoldResponse reserve(ReserveRequest request, String idempotencyKey) {
        Long merchantId = currentUserContext.currentMerchantId();
        String requestHash = idempotencyKeyService.hashRequest(request.giftCardCode(), request.amount().setScale(2, RoundingMode.HALF_UP).toPlainString());

        Optional<HoldResponse> cached = idempotencyKeyService.claim(merchantId, "reserve", idempotencyKey, requestHash, HoldResponse.class);
        if (cached.isPresent()) {
            log.info("Idempotency-Key {} already completed, returning cached result", idempotencyKey);
            return cached.get();
        }

        try {
            HoldResponse response = reserveWithoutIdempotency(merchantId, request);
            idempotencyKeyService.complete(merchantId, "reserve", idempotencyKey, response);
            return response;
        } catch (RuntimeException e) {
            idempotencyKeyService.discard(merchantId, "reserve", idempotencyKey);
            throw e;
        }
    }

    private HoldResponse reserveWithoutIdempotency(Long merchantId, ReserveRequest request) {
        // Locks the gift_card row for the whole transaction: the available-balance check below
        // reads across gift_card and gift_card_hold, so the invariant being protected is
        // per-gift-card and must be serialized at that granularity.
        GiftCard giftCard = giftCardRepository.findByMerchantIdAndCardCodeForUpdate(merchantId, request.giftCardCode())
                .orElseThrow(() -> new UnknownGiftCardException("Gift card not found"));

        giftCard.ensureUsable();

        // @Digits on ReserveRequest already rejects more than 2 decimal places, so this only pads
        // the scale (e.g. "1" -> "1.00") - see GiftCardService#executeRedemptionSync for the same pattern.
        BigDecimal amount = request.amount().setScale(2, RoundingMode.HALF_UP);

        BigDecimal pendingHeld = giftCardHoldRepository.sumPendingHoldAmounts(giftCard.getId());
        BigDecimal available = giftCard.getBalance().subtract(pendingHeld);

        if (available.compareTo(amount) < 0) {
            throw new InsufficientAvailableBalanceException("Insufficient available balance to reserve this amount");
        }

        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(holdTtlMinutes);
        GiftCardHold hold = new GiftCardHold(giftCard, merchantId, amount, expiresAt);
        GiftCardHold saved = giftCardHoldRepository.save(hold);

        ledgerService.record(giftCard, merchantId, LedgerEntryType.HOLD_PLACED, amount, giftCard.getBalance(), saved.getId(), currentUserContext.currentUserIdOrNull(), currentUserContext.isApiKeyAuthenticated());

        log.info("Reserved hold [{}] for {} on card [{}]", saved.getId(), amount, request.giftCardCode());

        return new HoldResponse(saved.getId(), saved.getStatus().name(), saved.getExpiresAt(), available.subtract(amount));
    }

    @Transactional
    public HoldResponse capture(Long holdId) {
        Long merchantId = currentUserContext.currentMerchantId();

        GiftCardHold hold = giftCardHoldRepository.findByIdAndMerchantIdForUpdate(holdId, merchantId)
                .orElseThrow(() -> new HoldNotFoundException("Hold not found"));

        // Capture is idempotent by target state: retrying a capture that already succeeded
        // replays the same 200 response instead of erroring, since re-asking for CAPTURED on an
        // already-CAPTURED hold is not a real conflict. A different terminal state (RELEASED/
        // EXPIRED) is a genuine conflict and still throws.
        if (hold.getStatus() == HoldStatus.CAPTURED) {
            return new HoldResponse(hold.getId(), hold.getStatus().name(), hold.getExpiresAt(), null);
        }

        if (!hold.isPending()) {
            throw new HoldAlreadyFinalizedException("Hold is already " + hold.getStatus());
        }

        if (hold.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new HoldAlreadyFinalizedException("Hold has expired");
        }

        // Re-lock the gift card row to perform the real balance deduction - this is the only
        // point at which a hold's earmark becomes an actual balance mutation.
        GiftCard giftCard = giftCardRepository.findByMerchantIdAndCardCodeForUpdate(merchantId, hold.getGiftCard().getCardCode())
                .orElseThrow(() -> new UnknownGiftCardException("Gift card not found"));
        giftCard.deductBalance(hold.getAmount());
        giftCardRepository.save(giftCard);

        hold.capture();
        giftCardHoldRepository.save(hold);

        ledgerService.record(giftCard, merchantId, LedgerEntryType.HOLD_CAPTURED, hold.getAmount(), giftCard.getBalance(), hold.getId(), currentUserContext.currentUserIdOrNull(), currentUserContext.isApiKeyAuthenticated());

        log.info("Captured hold [{}], deducted {} from card [{}]", hold.getId(), hold.getAmount(), giftCard.getCardCode());

        return new HoldResponse(hold.getId(), hold.getStatus().name(), hold.getExpiresAt(), null);
    }

    @Transactional
    public HoldResponse release(Long holdId) {
        Long merchantId = currentUserContext.currentMerchantId();

        GiftCardHold hold = giftCardHoldRepository.findByIdAndMerchantIdForUpdate(holdId, merchantId)
                .orElseThrow(() -> new HoldNotFoundException("Hold not found"));

        // Same idempotent-by-target-state replay as capture(): retrying a release that already
        // succeeded returns the same 200 instead of erroring.
        if (hold.getStatus() == HoldStatus.RELEASED) {
            return new HoldResponse(hold.getId(), hold.getStatus().name(), hold.getExpiresAt(), null);
        }

        if (!hold.isPending()) {
            throw new HoldAlreadyFinalizedException("Hold is already " + hold.getStatus());
        }

        hold.release();
        giftCardHoldRepository.save(hold);

        ledgerService.record(hold.getGiftCard(), merchantId, LedgerEntryType.HOLD_RELEASED, hold.getAmount(), hold.getGiftCard().getBalance(), hold.getId(), currentUserContext.currentUserIdOrNull(), currentUserContext.isApiKeyAuthenticated());

        log.info("Released hold [{}]", hold.getId());

        return new HoldResponse(hold.getId(), hold.getStatus().name(), hold.getExpiresAt(), null);
    }

    /**
     * Invoked by HoldExpirationScheduler for each expiry candidate, in its own transaction so
     * one failure doesn't roll back the rest of the sweep. Not merchant-scoped: the scheduler
     * already selected candidates system-wide.
     */
    @Transactional
    public void expireIfStillPending(Long holdId) {
        giftCardHoldRepository.findByIdForUpdate(holdId).ifPresent(hold -> {
            if (hold.isPending()) {
                hold.expire();
                giftCardHoldRepository.save(hold);
                log.info("Expired hold [{}]", hold.getId());
            }
        });
    }
}
