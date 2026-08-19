package com.finovago.p2p.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "UserStatusResponse", description = "Response confirming a merchant user's active status after an activate/deactivate call.")
public record UserStatusResponse(
    @Schema(description = "Id of the affected user.", requiredMode = Schema.RequiredMode.REQUIRED)
    Long userId,

    @Schema(description = "Email of the affected user.", requiredMode = Schema.RequiredMode.REQUIRED)
    String email,

    @Schema(description = "Whether the user is now active.", requiredMode = Schema.RequiredMode.REQUIRED)
    boolean active
) {}
