package com.finovago.p2p.unit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.finovago.p2p.dto.DailyRedemptionStat;
import com.finovago.p2p.dto.DashboardStatsResponse;
import com.finovago.p2p.repository.DailyRedemptionCount;
import com.finovago.p2p.repository.GiftCardBalanceSummary;
import com.finovago.p2p.repository.GiftCardRepository;
import com.finovago.p2p.repository.LedgerEntryRepository;
import com.finovago.p2p.security.CurrentUserContext;
import com.finovago.p2p.service.GiftCardStatsService;

@ExtendWith(MockitoExtension.class)
class GiftCardStatsServiceUnitTest {
    private static final Long MERCHANT_ID = 1L;

    @Mock
    private GiftCardRepository giftCardRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private CurrentUserContext currentUserContext;

    private GiftCardStatsService giftCardStatsService;

    @BeforeEach
    void setUp() {
        giftCardStatsService = new GiftCardStatsService(giftCardRepository, ledgerEntryRepository, currentUserContext);
        lenient().when(currentUserContext.currentMerchantId()).thenReturn(MERCHANT_ID);
        // Mockito can't have a mock's own when()/thenReturn() calls run *inside* another mock's
        // when()/thenReturn() call - it confuses Mockito's "ongoing stubbing" tracking and fails
        // with UnfinishedStubbing. Every helper-built mock below is therefore assigned to a local
        // variable first, then handed to thenReturn() on its own line.
        GiftCardBalanceSummary emptySummary = balanceSummary(BigDecimal.ZERO, 0, 0);
        lenient().when(giftCardRepository.getBalanceSummary(MERCHANT_ID)).thenReturn(emptySummary);
        lenient().when(ledgerEntryRepository.getDailyRedemptionCounts(eq(MERCHANT_ID), any())).thenReturn(List.of());
    }

    private static void assertMoneyEquals(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual), () -> "expected " + expected + " but was " + actual);
    }

    // lenient(): these are shared test-data builders - a given test may only care about some of the
    // fields (e.g. it overrides the summary from setUp() with its own), so not every stub here is
    // guaranteed to be exercised by every caller.
    private static GiftCardBalanceSummary balanceSummary(BigDecimal totalActiveBalance, long activeCards, long inactiveCards) {
        GiftCardBalanceSummary summary = mock(GiftCardBalanceSummary.class);
        lenient().when(summary.getTotalActiveBalance()).thenReturn(totalActiveBalance);
        lenient().when(summary.getActiveCards()).thenReturn(activeCards);
        lenient().when(summary.getInactiveCards()).thenReturn(inactiveCards);
        return summary;
    }

    private static DailyRedemptionCount dailyCount(LocalDate date, long count, BigDecimal totalAmount) {
        DailyRedemptionCount row = mock(DailyRedemptionCount.class);
        lenient().when(row.getEntryDate()).thenReturn(date);
        lenient().when(row.getRedemptionCount()).thenReturn(count);
        lenient().when(row.getTotalAmount()).thenReturn(totalAmount);
        return row;
    }

    @Test
    void should_return_active_balance_and_card_counts_from_repository_summary() {
        GiftCardBalanceSummary summary = balanceSummary(new BigDecimal("15420.50"), 142, 8);
        when(giftCardRepository.getBalanceSummary(MERCHANT_ID)).thenReturn(summary);

        DashboardStatsResponse response = giftCardStatsService.getDashboardStats(30);

        assertMoneyEquals(new BigDecimal("15420.50"), response.totalActiveBalance());
        assertEquals(142, response.activeCards());
        assertEquals(8, response.inactiveCards());
    }

    @Test
    void should_fill_missing_days_with_zero_count_and_amount() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        // 3-day window (today, yesterday, the day before) - only "yesterday" has ledger activity.
        DailyRedemptionCount yesterdayActivity = dailyCount(yesterday, 5, new BigDecimal("120.00"));
        when(ledgerEntryRepository.getDailyRedemptionCounts(eq(MERCHANT_ID), any())).thenReturn(List.of(yesterdayActivity));

        DashboardStatsResponse response = giftCardStatsService.getDashboardStats(3);

        assertEquals(3, response.redemptionsPerDay().size());
        DailyRedemptionStat dayBefore = response.redemptionsPerDay().get(0);
        DailyRedemptionStat yesterdayStat = response.redemptionsPerDay().get(1);
        DailyRedemptionStat todayStat = response.redemptionsPerDay().get(2);

        assertEquals(0, dayBefore.count());
        assertMoneyEquals(BigDecimal.ZERO.setScale(2), dayBefore.totalAmount());
        assertEquals(5, yesterdayStat.count());
        assertMoneyEquals(new BigDecimal("120.00"), yesterdayStat.totalAmount());
        assertEquals(0, todayStat.count());
        assertMoneyEquals(BigDecimal.ZERO.setScale(2), todayStat.totalAmount());
    }

    @Test
    void should_return_days_ordered_oldest_first() {
        DashboardStatsResponse response = giftCardStatsService.getDashboardStats(3);

        List<LocalDate> dates = response.redemptionsPerDay().stream().map(DailyRedemptionStat::date).toList();
        assertEquals(List.of(LocalDate.now().minusDays(2), LocalDate.now().minusDays(1), LocalDate.now()), dates);
    }

    @Test
    void should_sum_total_redeemed_in_period_across_all_days_including_zero_filled_ones() {
        LocalDate today = LocalDate.now();
        DailyRedemptionCount threeDaysAgoActivity = dailyCount(today.minusDays(2), 2, new BigDecimal("50.00"));
        DailyRedemptionCount todayActivity = dailyCount(today, 1, new BigDecimal("30.00"));
        when(ledgerEntryRepository.getDailyRedemptionCounts(eq(MERCHANT_ID), any()))
                .thenReturn(List.of(threeDaysAgoActivity, todayActivity));

        DashboardStatsResponse response = giftCardStatsService.getDashboardStats(3);

        assertMoneyEquals(new BigDecimal("80.00"), response.totalRedeemedInPeriod());
    }

    @Test
    void should_query_repository_with_window_start_based_on_days_parameter() {
        ArgumentCaptor<LocalDateTime> sinceCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

        giftCardStatsService.getDashboardStats(7);

        verify(ledgerEntryRepository).getDailyRedemptionCounts(eq(MERCHANT_ID), sinceCaptor.capture());
        assertEquals(LocalDate.now().minusDays(6).atStartOfDay(), sinceCaptor.getValue());
    }

    @Test
    void should_scope_both_queries_to_merchant_from_current_user_context() {
        giftCardStatsService.getDashboardStats(30);

        verify(giftCardRepository).getBalanceSummary(MERCHANT_ID);
        verify(ledgerEntryRepository).getDailyRedemptionCounts(eq(MERCHANT_ID), any());
    }
}
