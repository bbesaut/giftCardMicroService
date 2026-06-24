package com.finovago.p2p.controller;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.finovago.p2p.dto.GiftCardCreateRequest;
import com.finovago.p2p.dto.GiftCardResponse;
import com.finovago.p2p.dto.RedemptionResponse;
import com.finovago.p2p.service.GiftCardService;

import jakarta.validation.Valid;


@RestController
@RequestMapping("/api/giftcards")
@CrossOrigin(origins = "http://localhost:3000")
public class GiftCardController
{
    private static final Logger log = LoggerFactory.getLogger(GiftCardController.class);
    private final GiftCardService giftCardService;

    public GiftCardController(GiftCardService giftCardService) {
        this.giftCardService = giftCardService;
    }
    
    // Filter(creates the MDC context & correlationId) -> Controllers Thread has the MDC context and calls redeemGiftCardAsync 
    // -> MdcTaskDecorator called and copies the MDC context to his thread -> do the stuff -> clear MDC from the async thread 
    // -> returns the result to the controller thread -> controller thread returns the result to the client -> Filter clears the MDC context from Controllers Thread
    @PostMapping("/redeem/{giftCardCode}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CompletableFuture<RedemptionResponse> redeemGiftCard(@PathVariable String giftCardCode,@RequestParam double amount) {
        log.info("Received redemption request. Code: {}, Amount: {}", giftCardCode, amount);

        return giftCardService.redeemGiftCardAsync(
                giftCardCode,
                amount
        );
    }

    @GetMapping("/list")
    @ResponseStatus(HttpStatus.OK) 
    public List<GiftCardResponse> listGiftCards() {
        log.info("Received request to list all gift cards");
        return giftCardService.getAllGiftCards();
    }

    @PostMapping("/create")
    @ResponseStatus(HttpStatus.CREATED)
    public GiftCardResponse createGiftCard(@Valid @RequestBody GiftCardCreateRequest request) {
        log.info("Received gift card creation request. Code: {}, Balance: {}", 
                request.giftCardCode(), request.balance());
        
        return giftCardService.createGiftCard(request);
    }
}

