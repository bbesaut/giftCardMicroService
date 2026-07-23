package com.finovago.p2p.repository;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import com.finovago.p2p.model.GiftCard;

public interface GiftCardRepository extends JpaRepository<GiftCard, Long> {
    Optional<GiftCard> findByMerchantIdAndCardCode(Long merchantId, String cardCode);
    List<GiftCard> findAllByMerchantId(Long merchantId);

    // Locks the gift card row for the duration of the transaction; used by reserve/capture to
    // serialize concurrent hold operations against the same card's balance.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GiftCard g where g.merchant.id = :merchantId and g.cardCode = :cardCode")
    Optional<GiftCard> findByMerchantIdAndCardCodeForUpdate(@Param("merchantId") Long merchantId, @Param("cardCode") String cardCode);
}
