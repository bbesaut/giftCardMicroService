package com.finovago.p2p.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserContext {

    public boolean isAdmin() {
        return "ADMIN".equals(currentUser().role());
    }

    public Long currentMerchantId() {
        Long merchantId = currentUser().merchantId();
        if (merchantId == null) {
            throw new IllegalStateException("No merchant associated with the current authenticated user");
        }
        return merchantId;
    }

    private AuthenticatedUser currentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof AuthenticatedUser authenticatedUser)) {
            throw new IllegalStateException("No authenticated user in security context");
        }
        return authenticatedUser;
    }
}
