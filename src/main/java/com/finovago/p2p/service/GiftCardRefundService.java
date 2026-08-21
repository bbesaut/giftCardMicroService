package com.finovago.p2p.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finovago.p2p.dto.RefundRequest;
import com.finovago.p2p.dto.RefundResponse;
import com.finovago.p2p.exception.InvalidRefundTargetException;
import com.finovago.p2p.exception.LedgerEntryNotFoundException;
import com.finovago.p2p.exception.RefundExceedsOriginalAmountException;
import com.finovago.p2p.exception.UnknownGiftCardException;
import com.finovago.p2p.model.GiftCard;
import com.finovago.p2p.model.LedgerEntry;
import com.finovago.p2p.model.LedgerEntryType;
import com.finovago.p2p.repository.GiftCardRepository;
import com.finovago.p2p.repository.LedgerEntryRepository;
import com.finovago.p2p.security.CurrentUserContext;

/**
 * Reverses a specific prior REDEMPTION entry, capped at what's left to refund on it. Open to any
 * authenticated merchant account (human or service) - unlike GiftCardCreditService's free-form
 * adjustment, a refund is structurally bounded by a real prior transaction, so there's no extra
 * "a human must assert this" safety net needed here.
 */
@Service
public class GiftCardRefundService {
    private static final Logger log = LoggerFactory.getLogger(GiftCardRefundService.class);

    private final GiftCardRepository giftCardRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final CurrentUserContext currentUserContext;
    private final IdempotencyKeyService idempotencyKeyService;
    private final LedgerService ledgerService;

    public GiftCardRefundService(
            GiftCardRepository giftCardRepository,
            LedgerEntryRepository ledgerEntryRepository,
            CurrentUserContext currentUserContext,
            IdempotencyKeyService idempotencyKeyService,
            LedgerService ledgerService) {
        this.giftCardRepository = giftCardRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.currentUserContext = currentUserContext;
        this.idempotencyKeyService = idempotencyKeyService;
        this.ledgerService = ledgerService;
    }

    /**
     * claim()/complete()/discard() call a different bean (IdempotencyKeyService), so their
     * REQUIRES_NEW transactions correctly nest inside this method's transaction despite it being a
     * single @Transactional method - see GiftCardHoldService#reserve for the same reasoning.
     */
    @Transactional
    public RefundResponse refund(RefundRequest request, String idempotencyKey) {
        Long merchantId = currentUserContext.currentMerchantId();
        String requestHash = idempotencyKeyService.hashRequest(
                request.giftCardCode(),
                request.amount().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                String.valueOf(request.redemptionLedgerEntryId()));

        Optional<RefundResponse> cached = idempotencyKeyService.claim(merchantId, "refund", idempotencyKey, requestHash, RefundResponse.class);
        if (cached.isPresent()) {
            log.info("Idempotency-Key {} already completed, returning cached result", idempotencyKey);
            return cached.get();
        }

        try {
            RefundResponse response = refundWithoutIdempotency(merchantId, request);
            idempotencyKeyService.complete(merchantId, "refund", idempotencyKey, response);
            return response;
        } catch (RuntimeException e) {
            idempotencyKeyService.discard(merchantId, "refund", idempotencyKey);
            throw e;
        }
    }

    private RefundResponse refundWithoutIdempotency(Long merchantId, RefundRequest request) {
        // Locks the gift_card row for the whole transaction, same as reserve()/capture(): the
        // refund cap check below reads across gift_card_ledger for this card and must be
        // serialized at that granularity.
        GiftCard giftCard = giftCardRepository.findByMerchantIdAndCardCodeForUpdate(merchantId, request.giftCardCode())
                .orElseThrow(() -> new UnknownGiftCardException("Gift card not found"));

        // Deliberately no ensureUsable() call: refunding exists specifically to fix a card that had
        // a problem, so blocking on inactive/expired status would defeat the purpose.

        LedgerEntry target = ledgerEntryRepository.findByIdAndGiftCardIdAndMerchantId(request.redemptionLedgerEntryId(), giftCard.getId(), merchantId)
                .orElseThrow(() -> new LedgerEntryNotFoundException("Ledger entry to refund not found"));

        if (target.getEntryType() != LedgerEntryType.REDEMPTION) {
            throw new InvalidRefundTargetException("Only a REDEMPTION entry can be refunded");
        }

        BigDecimal amount = request.amount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal alreadyRefunded = ledgerEntryRepository.sumRefundedAmountForEntry(request.redemptionLedgerEntryId());
        BigDecimal refundable = target.getAmount().subtract(alreadyRefunded);
        if (amount.compareTo(refundable) > 0) {
            throw new RefundExceedsOriginalAmountException("Refund amount exceeds what's left to refund on this entry (refundable: " + refundable + ")");
        }

        giftCard.creditBalance(amount);
        giftCardRepository.save(giftCard);

        ledgerService.record(giftCard, merchantId, LedgerEntryType.REFUND, amount, giftCard.getBalance(), null,
                currentUserContext.currentUserIdOrNull(), currentUserContext.isApiKeyAuthenticated(), request.redemptionLedgerEntryId(), request.reason());

        log.info("Refunded {} to card [{}] against ledger entry [{}], new balance {}", amount, request.giftCardCode(), request.redemptionLedgerEntryId(), giftCard.getBalance());

        return new RefundResponse("SUCCESS", amount, giftCard.getBalance());
    }
}
