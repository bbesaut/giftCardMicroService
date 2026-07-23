package com.finovago.p2p.dto;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response object for gift card hold operations (reserve/capture/release).")
public record HoldResponse(
    @Schema(description = "Identifier of the hold", example = "42")
    Long holdId,

    @Schema(description = "Current status of the hold", example = "PENDING")
    String status,

    @Schema(description = "Timestamp at which the hold expires and is auto-released if not captured/released before then", example = "2026-07-23T15:30:00")
    LocalDateTime expiresAt,

    @Schema(description = "Gift card balance still available for new holds after this operation. Populated on reserve only; null on capture/release.", example = "50.0")
    Double remainingAvailableBalance
) {}
