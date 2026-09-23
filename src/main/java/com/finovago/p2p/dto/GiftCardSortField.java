package com.finovago.p2p.dto;

// Whitelists which entity properties a client can sort the gift card list by, instead of accepting
// an arbitrary Spring Data "sort" string - that would let a client request a sort on any property
// name (including ones that don't exist, causing a query-time error) or one we'd rather not expose.
public enum GiftCardSortField {
    CARD_CODE("cardCode"),
    BALANCE("balance"),
    EXPIRATION_DATE("expirationDate");

    private final String propertyName;

    GiftCardSortField(String propertyName) {
        this.propertyName = propertyName;
    }

    public String getPropertyName() {
        return propertyName;
    }
}
