package com.finovago.p2p.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

@Schema(name = "CurrentUserResponse", description = "Profile of the authenticated user, for a front-end to bootstrap its session (who is logged in, which screens to show).")
public record CurrentUserResponse(
    @Schema(description = "Id of the authenticated user.", requiredMode = Schema.RequiredMode.REQUIRED)
    Long userId,

    @Schema(description = "Email of the authenticated user.", example = "client@finovago.com", requiredMode = Schema.RequiredMode.REQUIRED)
    String email,

    @Schema(description = "Role of the authenticated user.", allowableValues = {"ADMIN", "MERCHANT"}, requiredMode = Schema.RequiredMode.REQUIRED)
    String role,

    @Schema(description = "Whether the user is the owner of their merchant (can manage its users and API key). Always false for an ADMIN.", requiredMode = Schema.RequiredMode.REQUIRED)
    boolean owner,

    @Schema(description = "The merchant the user belongs to. Null for an ADMIN, who has no merchant of their own.", nullable = true)
    @Nullable MerchantSummary merchant
) {

    @Schema(name = "CurrentUserMerchant", description = "Minimal view of the merchant a user belongs to.")
    public record MerchantSummary(
        @Schema(description = "Id of the merchant.", requiredMode = Schema.RequiredMode.REQUIRED)
        Long id,

        @Schema(description = "Business name of the merchant.", example = "Finovago Demo Merchant", requiredMode = Schema.RequiredMode.REQUIRED)
        String name
    ) {}
}
