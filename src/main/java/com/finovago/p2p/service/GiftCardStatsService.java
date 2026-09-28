package com.finovago.p2p.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.finovago.p2p.dto.DailyRedemptionStat;
import com.finovago.p2p.dto.DashboardStatsResponse;
import com.finovago.p2p.repository.DailyRedemptionCount;
import com.finovago.p2p.repository.GiftCardBalanceSummary;
import com.finovago.p2p.repository.GiftCardRepository;
import com.finovago.p2p.repository.LedgerEntryRepository;
import com.finovago.p2p.security.CurrentUserContext;

@Service
public class GiftCardStatsService {
    private final GiftCardRepository giftCardRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final CurrentUserContext currentUserContext;

    public GiftCardStatsService(GiftCardRepository giftCardRepository, LedgerEntryRepository ledgerEntryRepository, CurrentUserContext currentUserContext) {
        this.giftCardRepository = giftCardRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.currentUserContext = currentUserContext;
    }

    public DashboardStatsResponse getDashboardStats(int days) {
        Long merchantId = currentUserContext.currentMerchantId();

        GiftCardBalanceSummary balanceSummary = giftCardRepository.getBalanceSummary(merchantId);

        // "days" calendar days including today, e.g. days=30 covers today and the 29 days before it.
        LocalDate startDate = LocalDate.now().minusDays(days - 1L);
        List<DailyRedemptionCount> dailyCounts = ledgerEntryRepository.getDailyRedemptionCounts(merchantId, startDate.atStartOfDay());
        Map<LocalDate, DailyRedemptionCount> countsByDate = dailyCounts.stream()
                .collect(Collectors.toMap(DailyRedemptionCount::getEntryDate, Function.identity()));

        // Zero-fill every day in the window, not just the ones with activity - a chart shouldn't
        // have to guess whether a missing day means "no redemptions" or "data not loaded yet".
        List<DailyRedemptionStat> redemptionsPerDay = startDate.datesUntil(LocalDate.now().plusDays(1))
                .map(date -> {
                    DailyRedemptionCount count = countsByDate.get(date);
                    return count != null
                            ? new DailyRedemptionStat(date, count.getRedemptionCount(), count.getTotalAmount())
                            : new DailyRedemptionStat(date, 0, BigDecimal.ZERO.setScale(2));
                })
                .toList();

        BigDecimal totalRedeemedInPeriod = redemptionsPerDay.stream()
                .map(DailyRedemptionStat::totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new DashboardStatsResponse(
                balanceSummary.getTotalActiveBalance(),
                balanceSummary.getActiveCards(),
                balanceSummary.getInactiveCards(),
                totalRedeemedInPeriod,
                redemptionsPerDay
        );
    }
}
