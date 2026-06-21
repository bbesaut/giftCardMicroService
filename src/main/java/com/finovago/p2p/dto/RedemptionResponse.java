package com.finovago.p2p.dto;

public record RedemptionResponse(
        String status,
        double deductedAmount,
        double remainingBalance,
        double remainingToPay
) {}
