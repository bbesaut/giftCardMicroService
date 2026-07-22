package com.finovago.p2p.repository;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.finovago.p2p.model.GiftCard;

public interface GiftCardRepository extends JpaRepository<GiftCard, Long> {
    Optional<GiftCard> findByMerchantIdAndCardCode(Long merchantId, String cardCode);
    List<GiftCard> findAllByMerchantId(Long merchantId);
}
