package com.finovago.p2p.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record GiftCardCreateRequest(
    @NotBlank(message = "The card code cannot be blank")
    String giftCardCode,
    
    @PositiveOrZero(message = "The balance cannot be negative")
    double balance,
    
    boolean active
) {}