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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.finovago.p2p.dto.GiftCardCreateRequest;
import com.finovago.p2p.dto.GiftCardResponse;
import com.finovago.p2p.dto.HoldResponse;
import com.finovago.p2p.dto.LedgerEntryResponse;
import com.finovago.p2p.dto.RedemptionResponse;
import com.finovago.p2p.dto.RedemptionRequest;
import com.finovago.p2p.dto.RefundRequest;
import com.finovago.p2p.dto.RefundResponse;
import com.finovago.p2p.dto.ReserveRequest;
import com.finovago.p2p.service.GiftCardHoldService;
import com.finovago.p2p.service.GiftCardRefundService;
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
    private final GiftCardHoldService giftCardHoldService;
    private final GiftCardRefundService giftCardRefundService;

    public GiftCardController(GiftCardService giftCardService, GiftCardHoldService giftCardHoldService, GiftCardRefundService giftCardRefundService) {
        this.giftCardService = giftCardService;
        this.giftCardHoldService = giftCardHoldService;
        this.giftCardRefundService = giftCardRefundService;
    }

    @Operation(
        summary = "Redeem a gift card",
        description = "Redeem a specified amount from a gift card using its code. The request will be processed asynchronously and returns a CompletableFuture. "
                    + "Requires authentication (JWT token). MDC correlation ID is automatically propagated to async threads. "
                    + "Requires the Idempotency-Key header: safely retry a redemption after a network failure by resending the same key - "
                    + "a completed request replays its cached result instead of deducting the balance again."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Accepted - Redemption request accepted and being processed asynchronously",
            content = @Content(schema = @Schema(implementation = RedemptionResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request - Invalid request body (missing or invalid fields) or missing Idempotency-Key header",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Bad Request\",\"message\":\"The gift card code cannot be blank\"}"))),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Unauthorized\",\"message\":\"Invalid or missing JWT token\"}"))),
        @ApiResponse(responseCode = "404", description = "Not Found - Gift card with specified code does not exist",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Not Found\",\"message\":\"Gift card not found\"}"))),
        @ApiResponse(responseCode = "409", description = "Conflict - Idempotency-Key already used with a different request payload, or a request with this key is still being processed",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Conflict\",\"message\":\"This Idempotency-Key was already used with a different request payload\"}"))),
        @ApiResponse(responseCode = "422", description = "Unprocessable Entity - Gift card is inactive or has expired. "
                    + "Note: an amount exceeding the balance is NOT an error - the response returns a SUCCESS status with a non-zero remainingToPay.",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Unprocessable Entity\",\"message\":\"Gift card has expired\"}"))),
        @ApiResponse(responseCode = "429", description = "Too Many Requests - Rate limit exceeded for this IP (max 10 attempts/minute)",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Too Many Requests\",\"message\":\"Too many requests. Please try again later.\"}"))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error - Unexpected server error",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Internal Server Error\",\"message\":\"Database error occurred\"}")))
    })
    @PostMapping("/redeem")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CompletableFuture<RedemptionResponse> redeemGiftCard(
            @Valid @RequestBody RedemptionRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        log.info("Received redemption request. Code: {}, Amount: {}, IdempotencyKey: {}", request.giftCardCode(), request.amount(), idempotencyKey);

        return giftCardService.redeemGiftCardAsync(request, idempotencyKey);
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
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Unauthorized\",\"message\":\"Invalid or missing JWT token\"}"))),
        @ApiResponse(responseCode = "403", description = "Forbidden - User role not permitted to list gift cards",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Forbidden\",\"message\":\"Insufficient permissions for this resource\"}"))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error - Database or unexpected server error",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Internal Server Error\",\"message\":\"Database error occurred\"}")))
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
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Bad Request\",\"message\":\"The card code cannot be blank\"}"))),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Unauthorized\",\"message\":\"Invalid or missing JWT token\"}"))),
        @ApiResponse(responseCode = "403", description = "Forbidden - Insufficient permissions (ADMIN role required)",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Forbidden\",\"message\":\"Insufficient permissions for this resource\"}"))),
        @ApiResponse(responseCode = "409", description = "Conflict - Gift card code already exists",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Conflict\",\"message\":\"Gift card with this code already exists\"}"))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error - Database or unexpected server error",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Internal Server Error\",\"message\":\"Database error occurred\"}")))
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
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Unauthorized\",\"message\":\"Invalid or missing JWT token\"}"))),
        @ApiResponse(responseCode = "404", description = "Not Found - Gift card with specified code does not exist",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Not Found\",\"message\":\"Gift card not found\"}"))),
        @ApiResponse(responseCode = "429", description = "Too Many Requests - Rate limit exceeded for this IP (max 10 attempts/minute)",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Too Many Requests\",\"message\":\"Too many requests. Please try again later.\"}"))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error - Database or unexpected server error",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Internal Server Error\",\"message\":\"Database error occurred\"}")))
    })
    @GetMapping("/lookup/{code}")
    @ResponseStatus(HttpStatus.OK)
    public GiftCardResponse lookupGiftCard(@PathVariable String code) {
        log.info("Received gift card lookup request. Code: {}", code);
        return giftCardService.lookupGiftCard(code);
    }

    @Operation(
        summary = "Get a gift card's ledger history",
        description = "Retrieve the full append-only history of balance-affecting operations (creation, redemptions, holds) for a specific gift card. "
                    + "Entries are returned oldest first. Useful for customer support investigations ('why did my balance change'). "
                    + "Requires authentication (JWT token, MERCHANT role)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK - Successfully retrieved the ledger history",
            content = @Content(schema = @Schema(implementation = LedgerEntryResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Unauthorized\",\"message\":\"Invalid or missing JWT token\"}"))),
        @ApiResponse(responseCode = "404", description = "Not Found - Gift card with specified code does not exist",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Not Found\",\"message\":\"Gift card not found\"}"))),
        @ApiResponse(responseCode = "429", description = "Too Many Requests - Rate limit exceeded for this IP (max 10 attempts/minute)",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Too Many Requests\",\"message\":\"Too many requests. Please try again later.\"}"))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error - Database or unexpected server error",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Internal Server Error\",\"message\":\"Database error occurred\"}")))
    })
    @GetMapping("/{code}/ledger")
    @ResponseStatus(HttpStatus.OK)
    public List<LedgerEntryResponse> getLedger(@PathVariable String code) {
        log.info("Received gift card ledger request. Code: {}", code);
        return giftCardService.getLedger(code);
    }

    @Operation(
        summary = "Reserve a hold on a gift card",
        description = "Earmark an amount against a gift card's balance without deducting it yet. The hold must later be "
                    + "confirmed via capture or cancelled via release, and auto-expires if left pending too long. "
                    + "Unlike redeem, an amount exceeding the available balance (balance minus other pending holds) IS an error. "
                    + "Requires authentication (JWT token, MERCHANT role). "
                    + "Requires the Idempotency-Key header: safely retry a reservation after a network failure by resending the same key - "
                    + "a completed request replays its cached result instead of reserving a second hold."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Created - Hold reserved, status PENDING",
            content = @Content(schema = @Schema(implementation = HoldResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request - Invalid request body (missing or invalid fields), or missing Idempotency-Key header",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Bad Request\",\"message\":\"The gift card code cannot be blank\"}"))),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Unauthorized\",\"message\":\"Invalid or missing JWT token\"}"))),
        @ApiResponse(responseCode = "404", description = "Not Found - Gift card with specified code does not exist",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Not Found\",\"message\":\"Gift card not found\"}"))),
        @ApiResponse(responseCode = "409", description = "Conflict - Idempotency-Key already used with a different request payload, or a request with this key is still being processed",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Conflict\",\"message\":\"This Idempotency-Key was already used with a different request payload\"}"))),
        @ApiResponse(responseCode = "422", description = "Unprocessable Entity - Card is inactive/expired, or available balance is insufficient to reserve this amount",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Unprocessable Entity\",\"message\":\"Insufficient available balance to reserve this amount\"}"))),
        @ApiResponse(responseCode = "429", description = "Too Many Requests - Rate limit exceeded for this IP (max 10 attempts/minute)",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Too Many Requests\",\"message\":\"Too many requests. Please try again later.\"}"))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error - Database or unexpected server error",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Internal Server Error\",\"message\":\"Database error occurred\"}")))
    })
    @PostMapping("/reserve")
    @ResponseStatus(HttpStatus.CREATED)
    public HoldResponse reserveGiftCard(
            @Valid @RequestBody ReserveRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        log.info("Received hold reservation request. Code: {}, Amount: {}, IdempotencyKey: {}", request.giftCardCode(), request.amount(), idempotencyKey);
        return giftCardHoldService.reserve(request, idempotencyKey);
    }

    @Operation(
        summary = "Capture a hold",
        description = "Finalize a previously reserved hold: deducts the held amount from the gift card's real balance. "
                    + "Idempotent by target state: retrying a capture that already succeeded (hold already CAPTURED) replays the same 200 response "
                    + "rather than erroring. Retrying against a hold in a different terminal state (RELEASED/EXPIRED) is a genuine conflict and still returns 409. "
                    + "Requires authentication (JWT token, MERCHANT role)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK - Hold captured, status CAPTURED (or already was CAPTURED - replayed)",
            content = @Content(schema = @Schema(implementation = HoldResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Unauthorized\",\"message\":\"Invalid or missing JWT token\"}"))),
        @ApiResponse(responseCode = "404", description = "Not Found - Hold with specified id does not exist for the caller's merchant",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Not Found\",\"message\":\"Hold not found\"}"))),
        @ApiResponse(responseCode = "409", description = "Conflict - Hold is in a different terminal state (RELEASED or EXPIRED)",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Conflict\",\"message\":\"Hold is already RELEASED\"}"))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error - Database or unexpected server error",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Internal Server Error\",\"message\":\"Database error occurred\"}")))
    })
    @PostMapping("/holds/{holdId}/capture")
    @ResponseStatus(HttpStatus.OK)
    public HoldResponse captureHold(@PathVariable Long holdId) {
        log.info("Received hold capture request. HoldId: {}", holdId);
        return giftCardHoldService.capture(holdId);
    }

    @Operation(
        summary = "Release a hold",
        description = "Cancel a previously reserved hold: no balance deduction occurs and the held amount becomes available again. "
                    + "Idempotent by target state: retrying a release that already succeeded (hold already RELEASED) replays the same 200 response "
                    + "rather than erroring. Retrying against a hold in a different terminal state (CAPTURED/EXPIRED) is a genuine conflict and still returns 409. "
                    + "Requires authentication (JWT token, MERCHANT role)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK - Hold released, status RELEASED (or already was RELEASED - replayed)",
            content = @Content(schema = @Schema(implementation = HoldResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Unauthorized\",\"message\":\"Invalid or missing JWT token\"}"))),
        @ApiResponse(responseCode = "404", description = "Not Found - Hold with specified id does not exist for the caller's merchant",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Not Found\",\"message\":\"Hold not found\"}"))),
        @ApiResponse(responseCode = "409", description = "Conflict - Hold is in a different terminal state (CAPTURED or EXPIRED)",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Conflict\",\"message\":\"Hold is already CAPTURED\"}"))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error - Database or unexpected server error",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Internal Server Error\",\"message\":\"Database error occurred\"}")))
    })
    @PostMapping("/holds/{holdId}/release")
    @ResponseStatus(HttpStatus.OK)
    public HoldResponse releaseHold(@PathVariable Long holdId) {
        log.info("Received hold release request. HoldId: {}", holdId);
        return giftCardHoldService.release(holdId);
    }

    @Operation(
        summary = "Refund a gift card against a prior redemption",
        description = "Reverses a specific prior REDEMPTION entry (identified by redemptionLedgerEntryId, from GET /{code}/ledger), "
                    + "exclusively through a new REFUND ledger entry (never a direct balance edit). Capped at what's left to refund "
                    + "on that entry (its original amount minus any prior refunds against it). Callable by any authenticated merchant "
                    + "account (human or the merchant's own service/integration account) - unlike /credit, a refund is structurally "
                    + "bounded by a real prior transaction, so no extra restriction is needed. "
                    + "Deliberately allowed on an inactive/expired card - refunding exists specifically to fix a problem, so blocking on "
                    + "that same problem's status would defeat the purpose. Requires authentication (JWT token, MERCHANT role). "
                    + "Requires the Idempotency-Key header: safely retry after a network failure by resending the same key - "
                    + "a completed request replays its cached result instead of refunding a second time."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK - Refund applied",
            content = @Content(schema = @Schema(implementation = RefundResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request - Invalid request body (missing or invalid fields) or missing Idempotency-Key header",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Bad Request\",\"message\":\"The gift card code cannot be blank\"}"))),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Unauthorized\",\"message\":\"Invalid or missing JWT token\"}"))),
        @ApiResponse(responseCode = "404", description = "Not Found - Gift card does not exist for the caller's merchant, or redemptionLedgerEntryId does not reference an entry on this card",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Not Found\",\"message\":\"Gift card not found\"}"))),
        @ApiResponse(responseCode = "409", description = "Conflict - Idempotency-Key already used with a different request payload, or a request with this key is still being processed",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Conflict\",\"message\":\"This Idempotency-Key was already used with a different request payload\"}"))),
        @ApiResponse(responseCode = "422", description = "Unprocessable Entity - redemptionLedgerEntryId does not reference a REDEMPTION entry, or the refund amount exceeds what's left to refund on it",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Unprocessable Entity\",\"message\":\"Refund amount exceeds what's left to refund on this entry\"}"))),
        @ApiResponse(responseCode = "429", description = "Too Many Requests - Rate limit exceeded for this merchant (max 300 requests/minute)",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Too Many Requests\",\"message\":\"Too many requests. Please try again later.\"}"))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error - Unexpected server error",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"error\":\"Internal Server Error\",\"message\":\"Database error occurred\"}")))
    })
    @PostMapping("/refund")
    @ResponseStatus(HttpStatus.OK)
    public RefundResponse refundGiftCard(
            @Valid @RequestBody RefundRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        log.info("Received refund request. Code: {}, Amount: {}, RedemptionLedgerEntryId: {}, IdempotencyKey: {}",
                request.giftCardCode(), request.amount(), request.redemptionLedgerEntryId(), idempotencyKey);
        return giftCardRefundService.refund(request, idempotencyKey);
    }
}
