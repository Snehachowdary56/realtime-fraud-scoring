package com.sneha.fraud.it;

import com.sneha.fraud.api.TransactionRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Generates realistic card traffic with labelled, planted fraud.
 * History: 30 days of normal spending per account. Live: a day of normal spending plus four fraud patterns.
 */
final class SyntheticStream {
    record Labelled(TransactionRequest txn, boolean fraud, String pattern) {}

    static final String[] CATEGORIES = {"groceries", "restaurants", "fuel", "pharmacy", "online_retail", "transit"};
    final List<TransactionRequest> history = new ArrayList<>();
    final List<Labelled> live = new ArrayList<>();
    private final Random rnd;
    private final Instant liveStart;
    private int seq;
    private final String runId;

    SyntheticStream(long seed, int accounts, Instant liveStart, String runId) {
        this.rnd = new Random(seed);
        this.liveStart = liveStart;
        this.runId = runId;
        for (int a = 0; a < accounts; a++) {
            String account = String.format("acct-%s-%03d", runId, a);
            String device = "dev-" + account;
            double typical = 25 + rnd.nextDouble() * 75;          // each account spends 25-100 on average
            // 30 days of history, about 2 purchases a day
            Instant t = liveStart.minus(Duration.ofDays(30));
            while (t.isBefore(liveStart.minus(Duration.ofHours(2)))) {
                history.add(txn(account, normalAmount(typical), pick(), "CA", device, t));
                t = t.plus(Duration.ofMinutes(300 + rnd.nextInt(600)));
            }
            // live day: normal purchases spaced at least 90 minutes apart
            Instant lt = liveStart.plus(Duration.ofMinutes(rnd.nextInt(60)));
            for (int i = 0; i < 6; i++) {
                live.add(new Labelled(txn(account, normalAmount(typical), pick(), "CA", device, lt), false, "normal"));
                lt = lt.plus(Duration.ofMinutes(90 + rnd.nextInt(120)));
            }
            // plant fraud on every 5th account, rotating through four patterns, after the normal day
            if (a % 5 == 0) plantFraud(account, typical, lt.plus(Duration.ofHours(2)), (a / 5) % 4);
        }
        live.sort(Comparator.comparing(l -> l.txn().timestamp()));
    }

    private void plantFraud(String account, double typical, Instant t, int pattern) {
        switch (pattern) {
            case 0 -> { // card testing: 6 small purchases in 4 minutes; purchases 5 and 6 are the attack signal
                for (int i = 0; i < 6; i++) {
                    boolean flaggable = i >= 4;
                    live.add(new Labelled(txn(account, 1 + rnd.nextInt(5), "online_retail", "CA", "dev-" + account,
                            t.plus(Duration.ofSeconds(40L * i))), flaggable, flaggable ? "velocity" : "velocity-early"));
                }
            }
            case 1 -> live.add(new Labelled(txn(account, Math.max(600, typical * 12), "electronics", "CA",
                    "dev-" + account, t), true, "high-amount"));
            case 2 -> live.add(new Labelled(txn(account, typical, "online_retail", "BR", "dev-" + account,
                    liveLast(account).plus(Duration.ofMinutes(25))), true, "impossible-travel"));
            default -> live.add(new Labelled(txn(account, Math.max(700, typical * 10), "gift_cards", "CA",
                    "dev-stolen-" + account, t), true, "takeover"));
        }
    }

    private Instant liveLast(String account) {
        return live.stream().filter(l -> l.txn().accountId().equals(account))
                .map(l -> l.txn().timestamp()).max(Comparator.naturalOrder()).orElse(liveStart);
    }

    /** "velocity-early" purchases look normal by design: they are not counted either way. */
    static boolean scored(Labelled l) {
        return !l.pattern().equals("velocity-early");
    }

    private double normalAmount(double typical) {
        double v = typical * Math.exp(rnd.nextGaussian() * 0.35);   // log-normal spread around the typical amount
        return Math.max(2, v);
    }

    private String pick() {
        return CATEGORIES[rnd.nextInt(CATEGORIES.length)];
    }

    private TransactionRequest txn(String account, double amount, String cat, String country, String device, Instant at) {
        return new TransactionRequest("txn-" + runId + "-" + (seq++), account,
                BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP), cat, country, device, at);
    }
}
