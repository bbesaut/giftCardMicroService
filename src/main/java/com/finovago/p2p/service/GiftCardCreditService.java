package com.finovago.p2p.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finovago.p2p.dto.CreditRequest;
import com.finovago.p2p.dto.CreditResponse;
import com.finovago.p2p.exception.ServiceAccountNotAllowedException;
import com.finovago.p2p.exception.UnknownGiftCardException;
import com.finovago.p2p.model.GiftCard;
import com.finovago.p2p.model.LedgerEntryType;
import com.finovago.p2p.model.User;
import com.finovago.p2p.repository.GiftCardRepository;
import com.finovago.p2p.repository.UserRepository;
import com.finovago.p2p.security.CurrentUserContext;

/**
 * Free-form manual credit, not tied to any prior transaction - no structural cap, so unlike
 * GiftCardRefundService this requires a written reason and can only be triggered by a human
 * merchant account, never the merchant's own automated integration (service account). That's the
 * last safety net against a bug or runaway script silently inflating a card's balance.
 */
@Service
public class GiftCardCreditService {
    private static final Logger log = LoggerFactory.getLogger(GiftCardCreditService.class);

    private final GiftCardRepository giftCardRepository;
    private final UserRepository userRepository;
    private final CurrentUserContext currentUserContext;
    private final IdempotencyKeyService idempotencyKeyService;
    private final LedgerService ledgerService;

    public GiftCardCreditService(
            GiftCardRepository giftCardRepository,
            UserRepository userRepository,
            CurrentUserContext currentUserContext,
            IdempotencyKeyService idempotencyKeyService,
            LedgerService ledgerService) {
        this.giftCardRepository = giftCardRepository;
        this.userRepository = userRepository;
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
    public CreditResponse credit(CreditRequest request, String idempotencyKey) {
        Long merchantId = currentUserContext.currentMerchantId();
        requireHumanActor();

        String requestHash = idempotencyKeyService.hashRequest(
                request.giftCardCode(),
                request.amount().setScale(2, RoundingMode.HALF_UP).toPlainString());

        Optional<CreditResponse> cached = idempotencyKeyService.claim(merchantId, idempotencyKey, requestHash, CreditResponse.class);
        if (cached.isPresent()) {
            log.info("Idempotency-Key {} already completed, returning cached result", idempotencyKey);
            return cached.get();
        }

        try {
            CreditResponse response = creditWithoutIdempotency(merchantId, request);
            idempotencyKeyService.complete(merchantId, idempotencyKey, response);
            return response;
        } catch (RuntimeException e) {
            idempotencyKeyService.discard(merchantId, idempotencyKey);
            throw e;
        }
    }

    private void requireHumanActor() {
        Long userId = currentUserContext.currentUserIdOrNull();
        User user = userId == null ? null : userRepository.findById(userId).orElse(null);
        if (user == null || user.isServiceAccount()) {
            throw new ServiceAccountNotAllowedException("Manual credits must be performed by a human account, not a service account");
        }
    }

    private CreditResponse creditWithoutIdempotency(Long merchantId, CreditRequest request) {
        // Locks the gift_card row for the whole transaction, same as reserve()/capture().
        GiftCard giftCard = giftCardRepository.findByMerchantIdAndCardCodeForUpdate(merchantId, request.giftCardCode())
                .orElseThrow(() -> new UnknownGiftCardException("Gift card not found"));

        // Deliberately no ensureUsable() call: crediting exists specifically to fix a card that had
        // a problem, so blocking on inactive/expired status would defeat the purpose.

        BigDecimal amount = request.amount().setScale(2, RoundingMode.HALF_UP);

        giftCard.creditBalance(amount);
        giftCardRepository.save(giftCard);

        ledgerService.record(giftCard, merchantId, LedgerEntryType.ADJUSTMENT, amount, giftCard.getBalance(), null,
                currentUserContext.currentUserIdOrNull(), null, request.reason());

        log.info("Credited {} to card [{}], new balance {}", amount, request.giftCardCode(), giftCard.getBalance());

        return new CreditResponse("SUCCESS", amount, giftCard.getBalance());
    }
}
