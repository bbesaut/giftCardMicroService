package com.finovago.p2p.dto;

public record GiftCardResponse(
    String giftCardCode,
    double balance,
    boolean active
) {}