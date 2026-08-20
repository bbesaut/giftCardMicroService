package com.finovago.p2p.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    name = "RegisterResponse",
    description = "Response for merchant registration. Contains tokens for the newly created human owner account, "
                + "plus the credentials of the automated service account created alongside it. The service account "
                + "password is shown here once only and cannot be retrieved again."
)
public record RegisterResponse(
    @Schema(description = "Access and refresh tokens for the newly created owner account.", requiredMode = Schema.RequiredMode.REQUIRED)
    AuthResponse owner,

    @Schema(description = "Email of the automated service/integration account created for this merchant.",
        example = "finovago_service_account+42@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    String serviceAccountEmail,

    @Schema(description = "Plaintext password of the service account. Shown only in this response - store it securely.",
        requiredMode = Schema.RequiredMode.REQUIRED)
    String serviceAccountPassword
) {}
