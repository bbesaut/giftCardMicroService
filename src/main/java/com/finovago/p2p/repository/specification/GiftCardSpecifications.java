package com.finovago.p2p.repository.specification;

import org.springframework.data.jpa.domain.Specification;

import com.finovago.p2p.model.GiftCard;

// Each method always returns a non-null Specification - Specification#and(other) rejects a null
// "other" argument outright (Assert.notNull), so a filter that isn't set returns
// Specification.unrestricted() (a no-op, always-true predicate) instead of null. That's what lets
// the service chain every filter unconditionally without an if/else per optional query param.
public final class GiftCardSpecifications {

    private GiftCardSpecifications() {
    }

    public static Specification<GiftCard> belongsToMerchant(Long merchantId) {
        return (root, query, cb) -> cb.equal(root.get("merchant").get("id"), merchantId);
    }

    public static Specification<GiftCard> hasActive(Boolean active) {
        if (active == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.equal(root.get("active"), active);
    }

    public static Specification<GiftCard> codeContains(String code) {
        if (code == null || code.isBlank()) {
            return Specification.unrestricted();
        }
        String pattern = "%" + code.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("cardCode")), pattern);
    }
}
