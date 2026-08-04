package com.finovago.p2p.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import com.finovago.p2p.model.GiftCard;
import com.finovago.p2p.model.LedgerEntry;
import com.finovago.p2p.model.LedgerEntryType;
import com.finovago.p2p.repository.LedgerEntryRepository;

@Service
public class LedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;

    public LedgerService(LedgerEntryRepository ledgerEntryRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    /**
     * Appends a ledger row. Never call this outside the same transaction as the balance
     * mutation it records - the two must commit or roll back together.
     */
    public void record(GiftCard giftCard, Long merchantId, LedgerEntryType entryType, BigDecimal amount, BigDecimal balanceAfter, Long referenceId) {
        ledgerEntryRepository.save(new LedgerEntry(giftCard, merchantId, entryType, amount, balanceAfter, referenceId));
    }
}
