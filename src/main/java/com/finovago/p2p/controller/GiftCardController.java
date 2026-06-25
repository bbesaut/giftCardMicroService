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
import com.finovago.p2p.dto.RedemptionRequest;
import com.finovago.p2p.service.GiftCardService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;


@RestController
@RequestMapping("/api/v1/giftcards")
@CrossOrigin(origins = "http://localhost:3000")
@Tag(name = "GiftCard Management", description = "Endpoints to manage gift cards, including creation, listing, and redemption.")
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
    @Operation(summary = "Redeem a gift card", description = "Redeem a specified amount from a gift card using its code. The request will be processed asynchronously, and the response will indicate the success or failure of the redemption.")
    @ApiResponse(responseCode = "202", description = "Accepted: The redemption request has been accepted and is being processed.")
    @PostMapping("/redeem")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CompletableFuture<RedemptionResponse> redeemGiftCard(@Valid @RequestBody RedemptionRequest request) {
        log.info("Received redemption request. Code: {}, Amount: {}", request.giftCardCode(), request.amount());

        return giftCardService.redeemGiftCardAsync(
                request.giftCardCode(),
                request.amount()
        );
    }

    @Operation(summary = "List all gift cards", description = "Retrieve a list of all available gift cards. Each gift card's details, including its code and balance, will be included in the response.")
    @ApiResponse(responseCode = "200", description = "OK: Successfully retrieved the list of gift cards.")
    @GetMapping("/list")
    @ResponseStatus(HttpStatus.OK) 
    public List<GiftCardResponse> listGiftCards() {
        log.info("Received request to list all gift cards");
        return giftCardService.getAllGiftCards();
    }

    @Operation(summary = "Create a new gift card", description = "Create a new gift card with the specified code and balance.")
    @ApiResponse(responseCode = "201", description = "Created: The gift card was successfully created.")
    @PostMapping("/create")
    @ResponseStatus(HttpStatus.CREATED)
    public GiftCardResponse createGiftCard(@Valid @RequestBody GiftCardCreateRequest request) {
        log.info("Received gift card creation request. Code: {}, Balance: {}", 
                request.giftCardCode(), request.balance());
        
        return giftCardService.createGiftCard(request);
    }
}

