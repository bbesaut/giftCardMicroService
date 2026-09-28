package com.finovago.p2p.repository;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import com.finovago.p2p.model.GiftCard;

public interface GiftCardRepository extends JpaRepository<GiftCard, Long>, JpaSpecificationExecutor<GiftCard> {
    Optional<GiftCard> findByMerchantIdAndCardCode(Long merchantId, String cardCode);

    // Locks the gift card row for the duration of the transaction; used by reserve/capture to
    // serialize concurrent hold operations against the same card's balance.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GiftCard g where g.merchant.id = :merchantId and g.cardCode = :cardCode")
    Optional<GiftCard> findByMerchantIdAndCardCodeForUpdate(@Param("merchantId") Long merchantId, @Param("cardCode") String cardCode);

    // Dashboard summary: total balance held on active cards plus active/inactive counts, in one
    // round trip. Every sum is coalesced to 0 so a merchant with zero cards gets zeros back instead
    // of nulls (the projection's getters are primitive `long`/non-null BigDecimal).
    @Query("select coalesce(sum(case when g.active = true then g.balance else 0 end), 0) as totalActiveBalance, "
            + "coalesce(sum(case when g.active = true then 1L else 0L end), 0) as activeCards, "
            + "coalesce(sum(case when g.active = false then 1L else 0L end), 0) as inactiveCards "
            + "from GiftCard g where g.merchant.id = :merchantId")
    GiftCardBalanceSummary getBalanceSummary(@Param("merchantId") Long merchantId);
}
