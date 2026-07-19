package com.finovago.p2p.service;
import java.time.LocalDate;
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
import com.finovago.p2p.exception.ExpiredGiftCardException;
import com.finovago.p2p.exception.InactiveGiftCardException;
import com.finovago.p2p.exception.UnknownGiftCardException;
import com.finovago.p2p.model.GiftCard;
import com.finovago.p2p.repository.GiftCardRepository;

@Service
public class GiftCardService {
    private final GiftCardRepository giftCardRepository;
    private final Executor taskExecutor;
    private static final Logger log = LoggerFactory.getLogger(GiftCardService.class);

    public GiftCardService(GiftCardRepository giftCardRepository, @Qualifier("taskExecutor") Executor taskExecutor) {
        this.giftCardRepository = giftCardRepository;
        this.taskExecutor = taskExecutor;
    }

    public CompletableFuture<RedemptionResponse> redeemGiftCardAsync(RedemptionRequest request) {
        return CompletableFuture.supplyAsync(() -> {
                return executeRedemptionSync(request.giftCardCode(), request.amount());
        }, taskExecutor);
    }

    @Transactional
    public RedemptionResponse executeRedemptionSync(String code, double amount) {

        log.info("Processing database validation for card code: {}", code);

        long startTime = System.currentTimeMillis();

        if (code == null || code.isEmpty()) {
            throw new IllegalArgumentException("Card code invalid");
        }

        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        GiftCard giftCard = giftCardRepository.findByCardCode(code)
                .orElseThrow(() -> new UnknownGiftCardException("Gift card not found"));

        if (!giftCard.isActive()) {
            throw new InactiveGiftCardException("Card is already inactive");
        }

        if (giftCard.getExpirationDate().isBefore(LocalDate.now())) {
            throw new ExpiredGiftCardException("Gift card has expired");
        }

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
        Optional<GiftCard> existingCard = giftCardRepository.findByCardCode(request.giftCardCode());
        if (existingCard.isPresent()) {
            throw new IllegalArgumentException("Gift card with this code already exists");
        }

        log.debug("Database command issued: Instantiating new entity record for code: {}", request.giftCardCode());

        GiftCard giftCard = new GiftCard(request.giftCardCode(), request.balance(), request.active(), request.expirationDate());
        GiftCard savedCard = giftCardRepository.save(giftCard);

        log.info("Administrative Event: Gift card [{}] successfully registered into database vault.", request.giftCardCode());

        return new GiftCardResponse(savedCard.getCardCode(), savedCard.getBalance(), savedCard.isActive(), savedCard.getExpirationDate());
    }

    @Transactional(readOnly = true)
    public List<GiftCardResponse> getAllGiftCards() {
        log.info("Fetching all gift cards from database");

        return giftCardRepository.findAll()
                .stream()
                .map(card -> new GiftCardResponse(card.getCardCode(), card.getBalance(), card.isActive(), card.getExpirationDate()))
                .toList();
    }

    @Transactional(readOnly = true)
    public GiftCardResponse lookupGiftCard(String code) {
        log.info("Looking up gift card with code: {}", code);

        GiftCard giftCard = giftCardRepository.findByCardCode(code)
                .orElseThrow(() -> new UnknownGiftCardException("Gift card not found"));

        return new GiftCardResponse(
                giftCard.getCardCode(),
                giftCard.getBalance(),
                giftCard.isActive(),
                giftCard.getExpirationDate()
        );
    }
}
