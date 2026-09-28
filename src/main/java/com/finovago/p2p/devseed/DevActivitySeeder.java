package com.finovago.p2p.devseed;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.finovago.p2p.devseed.SeedCatalog.CardKind;
import com.finovago.p2p.dto.CreditRequest;
import com.finovago.p2p.dto.HoldResponse;
import com.finovago.p2p.dto.RedemptionResponse;
import com.finovago.p2p.dto.RefundRequest;
import com.finovago.p2p.dto.ReserveRequest;
import com.finovago.p2p.security.AuthenticatedUser;
import com.finovago.p2p.service.GiftCardCreditService;
import com.finovago.p2p.service.GiftCardHoldService;
import com.finovago.p2p.service.GiftCardRefundService;
import com.finovago.p2p.service.GiftCardService;

/**
 * Writes a few weeks of believable activity for each merchant: redemptions, holds (captured,
 * released, expired), refunds and manual credits, made by the merchant's own users and API key.
 * <p>
 * Every operation goes through the real service, so each one is validated like a live call and
 * leaves the balances and ledger exactly as production code would. The services stamp everything
 * with the current time, so after each operation the rows it wrote are moved to the moment it is
 * pretending to have happened (see {@link SeedTimeMachine}). Operations are replayed oldest first,
 * which keeps each card's ledger in a consistent order: the reconciliation job trusts the most
 * recent entry's balance, so an out-of-order history would read as drift.
 */
@Component
@Profile("dev & seed")
class DevActivitySeeder {

    private static final Logger log = LoggerFactory.getLogger(DevActivitySeeder.class);

    /** How far back the history reaches - past the dashboard's default 30-day window, up to its 90-day maximum. */
    static final int HISTORY_DAYS = 60;

    /** Cards come into existence during the first stretch of the history, not all on day one. */
    private static final int CARD_CREATION_SPREAD_DAYS = 20;

    private static final int BUSINESS_START_MINUTE = 8 * 60;
    private static final int BUSINESS_MINUTES = 13 * 60;

    private static final int API_KEY_SHARE_PERCENT = 35;
    private static final int OVERSPEND_PERCENT = 3;
    private static final int FULL_REFUND_PERCENT = 65;

    // Cumulative thresholds of a 0-99 roll, see runRandomOperation.
    private static final int REDEMPTION_UPTO = 72;
    private static final int HOLD_CAPTURE_UPTO = 84;
    private static final int HOLD_RELEASE_UPTO = 88;
    private static final int HOLD_EXPIRE_UPTO = 90;
    private static final int REFUND_UPTO = 96;

    private static final BigDecimal MIN_USABLE_BALANCE = BigDecimal.valueOf(5);
    private static final BigDecimal MAX_OVERSPEND_BALANCE = BigDecimal.valueOf(100);

    private static final List<String> REFUND_REASONS = List.of("Customer return", "Wrong item", "Duplicate charge");
    private static final List<String> CREDIT_REASONS = List.of(
            "Goodwill gesture - support ticket", "Promotional bonus", "Compensation for a delayed order");

    private enum Operation { REDEMPTION, HOLD_CAPTURED, HOLD_RELEASED, HOLD_EXPIRED, REFUND, CREDIT }

    private final GiftCardService giftCardService;
    private final GiftCardHoldService holdService;
    private final GiftCardRefundService refundService;
    private final GiftCardCreditService creditService;
    private final SeedTimeMachine timeMachine;
    private final Duration holdTtl;

    DevActivitySeeder(GiftCardService giftCardService, GiftCardHoldService holdService,
            GiftCardRefundService refundService, GiftCardCreditService creditService,
            SeedTimeMachine timeMachine, @Value("${app.holds.ttl-minutes:15}") long holdTtlMinutes) {
        this.giftCardService = giftCardService;
        this.holdService = holdService;
        this.refundService = refundService;
        this.creditService = creditService;
        this.timeMachine = timeMachine;
        this.holdTtl = Duration.ofMinutes(holdTtlMinutes);
    }

    void seed(List<SeededMerchant> merchants) {
        merchants.forEach(merchant -> new Simulation(merchant).run());
    }

    /** A gift card as the simulation currently believes it to be - mirrors what the real ledger says, without re-reading it. */
    private static final class TrackedCard {
        private final SeededCard seeded;
        private BigDecimal balance;
        private boolean active = true;
        // When this card's ledger was last written to. Nothing may be stamped earlier than this: a card's
        // history has to read in the order it was written, or the reconciliation job trusts the wrong "latest" entry.
        private LocalDateTime lastEntryAt;

        private TrackedCard(SeededCard seeded, LocalDateTime createdAt) {
            this.seeded = seeded;
            this.lastEntryAt = createdAt;
            this.balance = seeded.initialBalance();
        }

        private void touch(LocalDateTime at) {
            this.lastEntryAt = at;
        }

        private String code() {
            return seeded.code();
        }

        /** Whether an operation at {@code at} could really have hit this card. */
        private boolean usableAt(LocalDateTime at) {
            LocalDate expiration = seeded.finalExpirationDate();
            return active
                    && balance.compareTo(MIN_USABLE_BALANCE) >= 0
                    && lastEntryAt.isBefore(at)
                    && (expiration == null || at.toLocalDate().isBefore(expiration));
        }
    }

    /** A redemption that can still be (partly) refunded. */
    private static final class Refundable {
        private final TrackedCard card;
        private final Long ledgerEntryId;
        private final BigDecimal original;
        private final LocalDateTime at;
        private BigDecimal refunded = BigDecimal.ZERO.setScale(2);

        private Refundable(TrackedCard card, Long ledgerEntryId, BigDecimal original, LocalDateTime at) {
            this.card = card;
            this.ledgerEntryId = ledgerEntryId;
            this.original = original;
            this.at = at;
        }

        private BigDecimal remaining() {
            return original.subtract(refunded);
        }
    }

    /** One merchant's replay. Holds the per-merchant state (cards, refundable redemptions, ledger cursor) the operations share. */
    private final class Simulation {
        private final SeededMerchant merchant;
        // Own stream, distinct from the one that picked card codes and balances.
        private final Random random;
        private final LocalDateTime now = LocalDateTime.now();
        private final List<TrackedCard> cards = new ArrayList<>();
        private final List<Refundable> refundables = new ArrayList<>();
        private final Map<Operation, Integer> counts = new EnumMap<>(Operation.class);
        private long lastLedgerId;

        private Simulation(SeededMerchant merchant) {
            this.merchant = merchant;
            this.random = new Random(merchant.spec().name().hashCode() * 31L + 17);
        }

        private void run() {
            lastLedgerId = timeMachine.latestLedgerId();
            backdateCardCreations();

            LocalDate today = LocalDate.now();
            for (int daysAgo = HISTORY_DAYS; daysAgo >= 0; daysAgo--) {
                for (LocalDateTime at : operationTimes(today.minusDays(daysAgo), daysAgo)) {
                    runRandomOperation(at);
                }
            }
            leavePendingHolds();

            log.info("Activity for {}: {}", merchant.spec().name(), counts);
        }

        private void backdateCardCreations() {
            LocalDateTime historyStart = LocalDate.now().minusDays(HISTORY_DAYS).atStartOfDay();
            for (SeededCard seeded : merchant.cards()) {
                LocalDateTime createdAt = historyStart
                        .plusDays(random.nextInt(CARD_CREATION_SPREAD_DAYS))
                        .plusMinutes(BUSINESS_START_MINUTE + random.nextInt(BUSINESS_MINUTES));
                timeMachine.moveCreationEntry(merchant.id(), seeded.code(), createdAt);
                cards.add(new TrackedCard(seeded, createdAt));
            }
        }

        /** Sorted timestamps for one day: quieter weekends, and a gentle upward trend so a chart isn't flat. */
        private List<LocalDateTime> operationTimes(LocalDate date, int daysAgo) {
            double weekendFactor = date.getDayOfWeek().getValue() >= 6 ? 0.5 : 1.0;
            double trend = 0.7 + 0.6 * (HISTORY_DAYS - daysAgo) / HISTORY_DAYS;
            double jitter = 0.5 + random.nextDouble();
            int count = (int) Math.round(merchant.spec().dailyActivity() * weekendFactor * trend * jitter);

            return IntStream.range(0, count)
                    .mapToObj(i -> date.atStartOfDay().plusMinutes(BUSINESS_START_MINUTE + random.nextInt(BUSINESS_MINUTES)))
                    .filter(at -> at.isBefore(now.minusMinutes(1)))
                    .sorted()
                    .toList();
        }

        private void runRandomOperation(LocalDateTime at) {
            int roll = random.nextInt(100);
            if (roll < REDEMPTION_UPTO) {
                redeem(at);
            } else if (roll < HOLD_CAPTURE_UPTO) {
                holdThenCapture(at);
            } else if (roll < HOLD_RELEASE_UPTO) {
                holdThenRelease(at);
            } else if (roll < HOLD_EXPIRE_UPTO) {
                holdThenExpire(at);
            } else if (roll < REFUND_UPTO) {
                refund(at);
            } else {
                credit(at);
            }
        }

        private void redeem(LocalDateTime at) {
            TrackedCard card = pickUsableCard(at);
            if (card == null) {
                return;
            }
            AuthenticatedUser actor = pickActor(false);
            // The rare overspend is the "card can't cover it" case: the redemption drains the card and reports what's
            // left to pay. Only on a small balance - "spending" a few thousand in one go isn't a purchase, it's an outlier.
            boolean overspend = card.balance.compareTo(MAX_OVERSPEND_BALANCE) <= 0 && random.nextInt(100) < OVERSPEND_PERCENT;
            BigDecimal amount = overspend
                    ? card.balance.add(BigDecimal.valueOf(20))
                    : purchaseAmount(card);

            RedemptionResponse response = giftCardService.executeRedemptionSync(
                    merchant.id(), card.code(), amount, actor.userId(), actor.apiKey());

            card.balance = response.remainingBalance();
            if (card.balance.signum() == 0) {
                card.active = false;
            }
            List<Long> written = stamp(at);
            card.touch(at);
            refundables.add(new Refundable(card, written.get(0), response.deductedAmount(), at));
            count(Operation.REDEMPTION);
        }

        private void holdThenCapture(LocalDateTime at) {
            TrackedCard card = pickUsableCard(at);
            if (card == null) {
                return;
            }
            AuthenticatedUser actor = pickActor(false);
            BigDecimal amount = purchaseAmount(card);

            HoldResponse hold = reserve(actor, card.code(), amount);
            stamp(at);
            SeedSecurityContext.runAs(actor, () -> holdService.capture(hold.holdId()));
            LocalDateTime capturedAt = soonAfter(at);
            stamp(capturedAt);
            timeMachine.moveHold(hold.holdId(), at, at.plus(holdTtl));

            card.balance = card.balance.subtract(amount);
            card.touch(capturedAt);
            count(Operation.HOLD_CAPTURED);
        }

        private void holdThenRelease(LocalDateTime at) {
            TrackedCard card = pickUsableCard(at);
            if (card == null) {
                return;
            }
            AuthenticatedUser actor = pickActor(false);

            HoldResponse hold = reserve(actor, card.code(), purchaseAmount(card));
            stamp(at);
            SeedSecurityContext.runAs(actor, () -> holdService.release(hold.holdId()));
            LocalDateTime releasedAt = soonAfter(at);
            stamp(releasedAt);
            timeMachine.moveHold(hold.holdId(), at, at.plus(holdTtl));
            card.touch(releasedAt);
            count(Operation.HOLD_RELEASED);
        }

        private void holdThenExpire(LocalDateTime at) {
            // An expired hold needs its TTL to have really elapsed by now; too recent, and it would be a lie.
            if (at.isAfter(now.minus(holdTtl).minusMinutes(1))) {
                redeem(at);
                return;
            }
            TrackedCard card = pickUsableCard(at);
            if (card == null) {
                return;
            }
            HoldResponse hold = reserve(pickActor(false), card.code(), purchaseAmount(card));
            stamp(at);
            timeMachine.moveHold(hold.holdId(), at, at.plus(holdTtl));
            // The same call the expiration sweeper makes, once the hold is past its TTL.
            holdService.expireIfStillPending(hold.holdId());
            card.touch(at);
            count(Operation.HOLD_EXPIRED);
        }

        private void refund(LocalDateTime at) {
            List<Refundable> candidates = refundables.stream()
                    .filter(r -> r.remaining().signum() > 0 && r.at.isBefore(at.minusMinutes(30)) && r.card.lastEntryAt.isBefore(at))
                    .toList();
            if (candidates.isEmpty()) {
                redeem(at);
                return;
            }
            Refundable target = candidates.get(random.nextInt(candidates.size()));
            BigDecimal amount = random.nextInt(100) < FULL_REFUND_PERCENT
                    ? target.remaining()
                    : target.remaining().divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
            if (amount.signum() <= 0) {
                return;
            }
            AuthenticatedUser actor = pickActor(false);
            String reason = random.nextBoolean() ? null : pick(REFUND_REASONS);

            SeedSecurityContext.runAs(actor, () -> refundService.refund(
                    new RefundRequest(target.card.code(), amount, target.ledgerEntryId, reason), idempotencyKey()));
            stamp(at);

            target.refunded = target.refunded.add(amount);
            target.card.balance = target.card.balance.add(amount);
            target.card.touch(at);
            count(Operation.REFUND);
        }

        private void credit(LocalDateTime at) {
            // Any card that already existed - crediting a drained or soon-dead card is exactly what support does.
            List<TrackedCard> existing = cards.stream().filter(c -> c.lastEntryAt.isBefore(at)).toList();
            if (existing.isEmpty()) {
                return;
            }
            TrackedCard card = existing.get(random.nextInt(existing.size()));
            BigDecimal amount = BigDecimal.valueOf(5L * (1 + random.nextInt(10))).setScale(2);
            // A credit must be asserted by a person, never an API key.
            AuthenticatedUser actor = pickActor(true);

            SeedSecurityContext.runAs(actor, () -> creditService.credit(
                    new CreditRequest(card.code(), amount, pick(CREDIT_REASONS)), idempotencyKey()));
            stamp(at);

            card.balance = card.balance.add(amount);
            card.touch(at);
            count(Operation.CREDIT);
        }

        /** Holds left open on cards that stay usable, with a long TTL so they're still pending whenever the dev looks. */
        private void leavePendingHolds() {
            AuthenticatedUser owner = merchant.principalOf(merchant.owner());
            List<TrackedCard> candidates = cards.stream()
                    .filter(c -> c.active && c.balance.compareTo(BigDecimal.valueOf(50)) >= 0)
                    .filter(c -> c.seeded.kind() != CardKind.DEACTIVATED && c.seeded.kind() != CardKind.EXPIRED)
                    .toList();
            for (int i = 0; i < merchant.spec().pendingHolds() && !candidates.isEmpty(); i++) {
                TrackedCard card = candidates.get(random.nextInt(candidates.size()));
                BigDecimal amount = BigDecimal.valueOf(5L * (2 + random.nextInt(7))).setScale(2);
                HoldResponse hold = reserve(owner, card.code(), amount);
                timeMachine.moveHold(hold.holdId(), now, now.plusDays(7));
            }
        }

        private HoldResponse reserve(AuthenticatedUser actor, String code, BigDecimal amount) {
            return SeedSecurityContext.call(actor, () -> holdService.reserve(new ReserveRequest(code, amount), idempotencyKey()));
        }

        /** Picks a card at random, weighted by balance: big cards see more traffic, small ones don't all drain. */
        private TrackedCard pickUsableCard(LocalDateTime at) {
            List<TrackedCard> usable = cards.stream().filter(c -> c.usableAt(at)).toList();
            if (usable.isEmpty()) {
                return null;
            }
            double target = random.nextDouble() * usable.stream().mapToDouble(c -> c.balance.doubleValue()).sum();
            for (TrackedCard card : usable) {
                target -= card.balance.doubleValue();
                if (target <= 0) {
                    return card;
                }
            }
            return usable.get(usable.size() - 1);
        }

        /** A human account of the merchant, or - for merchants that have one - the API key. */
        private AuthenticatedUser pickActor(boolean humanOnly) {
            if (!humanOnly && merchant.spec().withApiKey() && random.nextInt(100) < API_KEY_SHARE_PERCENT) {
                return merchant.apiKeyPrincipal();
            }
            return merchant.principalOf(merchant.users().get(random.nextInt(merchant.users().size())));
        }

        /** A purchase-sized amount (5 to 60), never more than the card holds. */
        private BigDecimal purchaseAmount(TrackedCard card) {
            return BigDecimal.valueOf(5L * (1 + random.nextInt(12))).setScale(2).min(card.balance);
        }

        /** A few minutes after {@code at}, within the hold TTL and never in the future. */
        private LocalDateTime soonAfter(LocalDateTime at) {
            LocalDateTime later = at.plusMinutes(1 + random.nextInt(14));
            return later.isAfter(now) ? now : later;
        }

        /** Moves the ledger rows the last operation wrote to {@code at}, returning their ids in write order. */
        private List<Long> stamp(LocalDateTime at) {
            List<Long> ids = timeMachine.moveLedgerEntriesAfter(lastLedgerId, at);
            if (!ids.isEmpty()) {
                lastLedgerId = Collections.max(ids);
            }
            return ids;
        }

        private String pick(List<String> options) {
            return options.get(random.nextInt(options.size()));
        }

        /** Drawn from the seeded stream rather than {@code randomUUID()}, so keys are as reproducible as everything else. */
        private String idempotencyKey() {
            return new UUID(random.nextLong(), random.nextLong()).toString();
        }

        private void count(Operation operation) {
            counts.merge(operation, 1, Integer::sum);
        }
    }
}
