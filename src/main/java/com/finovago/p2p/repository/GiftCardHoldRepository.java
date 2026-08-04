package com.finovago.p2p.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.finovago.p2p.model.GiftCardHold;
import com.finovago.p2p.model.HoldStatus;

public interface GiftCardHoldRepository extends JpaRepository<GiftCardHold, Long> {

    // Merchant-scoped, locked lookup used by capture/release. A hold belonging to another
    // merchant is treated the same as a missing one (Optional.empty()) - never leaked via 403.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from GiftCardHold h where h.id = :id and h.merchantId = :merchantId")
    Optional<GiftCardHold> findByIdAndMerchantIdForUpdate(@Param("id") Long id, @Param("merchantId") Long merchantId);

    // System-scoped, locked lookup used by the scheduler right before transitioning a single
    // expiry candidate row (candidates themselves are found via the unlocked query below).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from GiftCardHold h where h.id = :id")
    Optional<GiftCardHold> findByIdForUpdate(@Param("id") Long id);

    // Used by reserve() to compute availableBalance under the already-held gift_card lock.
    @Query("select coalesce(sum(h.amount), 0) from GiftCardHold h where h.giftCard.id = :giftCardId and h.status = 'PENDING'")
    BigDecimal sumPendingHoldAmounts(@Param("giftCardId") Long giftCardId);

    // Unlocked read for the scheduler's periodic sweep; each candidate is re-locked individually
    // via findByIdForUpdate right before being expired.
    List<GiftCardHold> findByStatusAndExpiresAtBefore(HoldStatus status, LocalDateTime cutoff);
}
