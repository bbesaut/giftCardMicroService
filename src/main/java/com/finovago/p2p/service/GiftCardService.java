package com.finovago.p2p.service;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import com.finovago.p2p.dto.GiftCardCreateRequest;
import com.finovago.p2p.dto.GiftCardResponse;
import com.finovago.p2p.dto.RedemptionResponse;
import com.finovago.p2p.dto.RedemptionRequest;
import com.finovago.p2p.exception.UnknownGiftCardException;
import com.finovago.p2p.model.GiftCard;
import com.finovago.p2p.model.LedgerEntryType;
import com.finovago.p2p.model.Merchant;
import com.finovago.p2p.repository.GiftCardRepository;
import com.finovago.p2p.repository.MerchantRepository;
import com.finovago.p2p.security.CurrentUserContext;

@Service
public class GiftCardService {
    private final GiftCardRepository giftCardRepository;
    private final MerchantRepository merchantRepository;
    private final CurrentUserContext currentUserContext;
    private final IdempotencyKeyService idempotencyKeyService;
    private final LedgerService ledgerService;
    private final Executor taskExecutor;
    private static final Logger log = LoggerFactory.getLogger(GiftCardService.class);

    public GiftCardService(
            GiftCardRepository giftCardRepository,
            MerchantRepository merchantRepository,
            CurrentUserContext currentUserContext,
            IdempotencyKeyService idempotencyKeyService,
            LedgerService ledgerService,
            @Qualifier("taskExecutor") Executor taskExecutor) {
        this.giftCardRepository = giftCardRepository;
        this.merchantRepository = merchantRepository;
        this.currentUserContext = currentUserContext;
        this.idempotencyKeyService = idempotencyKeyService;
        this.ledgerService = ledgerService;
        this.taskExecutor = taskExecutor;
    }

    public CompletableFuture<RedemptionResponse> redeemGiftCardAsync(RedemptionRequest request, String idempotencyKey) {
        Long merchantId = currentUserContext.currentMerchantId();
        return CompletableFuture.supplyAsync(() -> {
            String requestHash = idempotencyKeyService.hashRequest(request.giftCardCode(), String.valueOf(request.amount()));

            Optional<RedemptionResponse> cached = idempotencyKeyService.claim(merchantId, idempotencyKey, requestHash, RedemptionResponse.class);
            if (cached.isPresent()) {
                log.info("Idempotency-Key {} already completed, returning cached result", idempotencyKey);
                return cached.get();
            }

            try {
                RedemptionResponse response = executeRedemptionSync(merchantId, request.giftCardCode(), request.amount());
                idempotencyKeyService.complete(merchantId, idempotencyKey, response);
                return response;
            } catch (RuntimeException e) {
                idempotencyKeyService.discard(merchantId, idempotencyKey);
                throw e;
            }
        }, taskExecutor);
    }

    @Transactional
    public RedemptionResponse executeRedemptionSync(Long merchantId, String code, double amount) {

        log.info("Processing database validation for card code: {}", code);

        long startTime = System.currentTimeMillis();

        if (code == null || code.isEmpty()) {
            throw new IllegalArgumentException("Card code invalid");
        }

        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        GiftCard giftCard = giftCardRepository.findByMerchantIdAndCardCode(merchantId, code)
                .orElseThrow(() -> new UnknownGiftCardException("Gift card not found"));

        giftCard.ensureUsable();

        double deducted;
        double remainingToPay = 0;

        if (giftCard.getBalance() > amount) {
            giftCard.deductBalance(amount);
            deducted = amount;
        } else {
            deducted = giftCard.getBalance();
            remainingToPay = amount - giftCard.getBalance();
            giftCard.drainCard();
        }

        giftCardRepository.save(giftCard);

        ledgerService.record(giftCard, merchantId, LedgerEntryType.REDEMPTION, deducted, giftCard.getBalance(), null);

        long duration = System.currentTimeMillis() - startTime;

        log.info("Redemption done in {}ms. Remaining balance: {}", duration, giftCard.getBalance());

        return new RedemptionResponse(
                "SUCCESS",
                deducted,
                giftCard.getBalance(),
                remainingToPay
        );
    }


    @Transactional
    public GiftCardResponse createGiftCard(GiftCardCreateRequest request) {
        Long merchantId = currentUserContext.currentMerchantId();

        Optional<GiftCard> existingCard = giftCardRepository.findByMerchantIdAndCardCode(merchantId, request.giftCardCode());
        if (existingCard.isPresent()) {
            throw new IllegalArgumentException("Gift card with this code already exists");
        }

        log.debug("Database command issued: Instantiating new entity record for code: {}", request.giftCardCode());

        // merchantId is trusted (derived from the authenticated principal, never from the request body).
        // getReferenceById avoids an extra round-trip to load the full Merchant just to set the FK.
        Merchant merchant = merchantRepository.getReferenceById(merchantId);

        GiftCard giftCard = new GiftCard(merchant, request.giftCardCode(), request.balance(), request.active(), request.expirationDate());
        GiftCard savedCard = giftCardRepository.save(giftCard);

        ledgerService.record(savedCard, merchantId, LedgerEntryType.CREATION, savedCard.getBalance(), savedCard.getBalance(), null);

        log.info("Administrative Event: Gift card [{}] successfully registered into database vault.", request.giftCardCode());

        return new GiftCardResponse(savedCard.getCardCode(), savedCard.getBalance(), savedCard.isActive(), savedCard.getExpirationDate(), merchantId);
    }

    @Transactional(readOnly = true)
    public List<GiftCardResponse> getAllGiftCards() {
        log.info("Fetching all gift cards from database");

        List<GiftCard> cards = currentUserContext.isAdmin()
                ? giftCardRepository.findAll()
                : giftCardRepository.findAllByMerchantId(currentUserContext.currentMerchantId());

        return cards
                .stream()
                .map(card -> new GiftCardResponse(card.getCardCode(), card.getBalance(), card.isActive(), card.getExpirationDate(), card.getMerchant().getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public GiftCardResponse lookupGiftCard(String code) {
        log.info("Looking up gift card with code: {}", code);

        Long merchantId = currentUserContext.currentMerchantId();
        GiftCard giftCard = giftCardRepository.findByMerchantIdAndCardCode(merchantId, code)
                .orElseThrow(() -> new UnknownGiftCardException("Gift card not found"));

        return new GiftCardResponse(
                giftCard.getCardCode(),
                giftCard.getBalance(),
                giftCard.isActive(),
                giftCard.getExpirationDate(),
                merchantId
        );
    }
}
