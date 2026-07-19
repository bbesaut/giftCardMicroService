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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.finovago.p2p.dto.GiftCardCreateRequest;
import com.finovago.p2p.dto.GiftCardResponse;
import com.finovago.p2p.dto.RedemptionResponse;
import com.finovago.p2p.dto.RedemptionRequest;
import com.finovago.p2p.service.GiftCardService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;


@RestController
@RequestMapping("/api/v1/giftcards")
@CrossOrigin(origins = "http://localhost:3000")
@Tag(name = "Gift Cards", description = "Gift card management endpoints.")
public class GiftCardController
{
    private static final Logger log = LoggerFactory.getLogger(GiftCardController.class);
    private final GiftCardService giftCardService;

    public GiftCardController(GiftCardService giftCardService) {
        this.giftCardService = giftCardService;
    }
    
    @Operation(
        summary = "Redeem a gift card",
        description = "Redeem a specified amount from a gift card using its code. The request will be processed asynchronously and returns a CompletableFuture. "
                    + "Requires authentication (JWT token). MDC correlation ID is automatically propagated to async threads."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Accepted - Redemption request accepted and being processed asynchronously",
            content = @Content(schema = @Schema(implementation = RedemptionResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request - Invalid request body (missing or invalid fields)",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Bad Request\",\"message\":\"The gift card code cannot be blank\",\"code\":\"correlation-id\"}"))),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Unauthorized\",\"message\":\"Invalid or missing JWT token\",\"code\":\"unknown\"}"))),
        @ApiResponse(responseCode = "404", description = "Not Found - Gift card with specified code does not exist",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Not Found\",\"message\":\"Gift card not found\",\"code\":\"correlation-id\"}"))),
        @ApiResponse(responseCode = "422", description = "Unprocessable Entity - Gift card is inactive or has expired. "
                    + "Note: an amount exceeding the balance is NOT an error - the response returns a SUCCESS status with a non-zero remainingToPay.",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Unprocessable Entity\",\"message\":\"Gift card has expired\",\"code\":\"correlation-id\"}"))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error - Unexpected server error",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Internal Server Error\",\"message\":\"Database error occurred\",\"code\":\"correlation-id\"}")))
    })
    @PostMapping("/redeem")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CompletableFuture<RedemptionResponse> redeemGiftCard(@Valid @RequestBody RedemptionRequest request) {
        log.info("Received redemption request. Code: {}, Amount: {}", request.giftCardCode(), request.amount());

        return giftCardService.redeemGiftCardAsync(request);
    }

    @Operation(
        summary = "List all gift cards",
        description = "Retrieve a list of all available gift cards with their details (code, balance, creation date, etc.). "
                    + "Requires authentication (JWT token)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK - Successfully retrieved the list of gift cards",
            content = @Content(schema = @Schema(implementation = GiftCardResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Unauthorized\",\"message\":\"Invalid or missing JWT token\",\"code\":\"unknown\"}"))),
        @ApiResponse(responseCode = "403", description = "Forbidden - User role not permitted to list gift cards",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Forbidden\",\"message\":\"Insufficient permissions for this resource\",\"code\":\"correlation-id\"}"))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error - Database or unexpected server error",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Internal Server Error\",\"message\":\"Database error occurred\",\"code\":\"correlation-id\"}")))
    })
    @GetMapping("/list")
    @ResponseStatus(HttpStatus.OK)
    public List<GiftCardResponse> listGiftCards() {
        log.info("Received request to list all gift cards");
        return giftCardService.getAllGiftCards();
    }

    @Operation(
        summary = "Create a new gift card",
        description = "Create a new gift card with the specified code and initial balance. "
                    + "Requires authentication (JWT token) and ADMIN role. "
                    + "Gift card code must be unique."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Created - Gift card successfully created",
            content = @Content(schema = @Schema(implementation = GiftCardResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request - Invalid request body (missing or invalid fields)",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Bad Request\",\"message\":\"The card code cannot be blank\",\"code\":\"correlation-id\"}"))),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Unauthorized\",\"message\":\"Invalid or missing JWT token\",\"code\":\"unknown\"}"))),
        @ApiResponse(responseCode = "403", description = "Forbidden - Insufficient permissions (ADMIN role required)",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Forbidden\",\"message\":\"Insufficient permissions for this resource\",\"code\":\"correlation-id\"}"))),
        @ApiResponse(responseCode = "409", description = "Conflict - Gift card code already exists",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Conflict\",\"message\":\"Gift card with this code already exists\",\"code\":\"correlation-id\"}"))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error - Database or unexpected server error",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Internal Server Error\",\"message\":\"Database error occurred\",\"code\":\"correlation-id\"}")))
    })
    @PostMapping("/create")
    @ResponseStatus(HttpStatus.CREATED)
    public GiftCardResponse createGiftCard(@Valid @RequestBody GiftCardCreateRequest request) {
        log.info("Received gift card creation request. Code: {}, Balance: {}",
                request.giftCardCode(), request.balance());

        return giftCardService.createGiftCard(request);
    }

    @Operation(
        summary = "Lookup gift card details",
        description = "Retrieve detailed information about a specific gift card by its code. "
                    + "Returns the card's current balance, active status, and expiration date. "
                    + "Requires authentication (JWT token)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK - Successfully retrieved gift card details",
            content = @Content(schema = @Schema(implementation = GiftCardResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Unauthorized\",\"message\":\"Invalid or missing JWT token\",\"code\":\"unknown\"}"))),
        @ApiResponse(responseCode = "404", description = "Not Found - Gift card with specified code does not exist",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Not Found\",\"message\":\"Gift card not found\",\"code\":\"correlation-id\"}"))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error - Database or unexpected server error",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Internal Server Error\",\"message\":\"Database error occurred\",\"code\":\"correlation-id\"}")))
    })
    @GetMapping("/lookup/{code}")
    @ResponseStatus(HttpStatus.OK)
    public GiftCardResponse lookupGiftCard(@PathVariable String code) {
        log.info("Received gift card lookup request. Code: {}", code);
        return giftCardService.lookupGiftCard(code);
    }
}

