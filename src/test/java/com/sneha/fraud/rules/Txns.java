package com.sneha.fraud.rules;

import com.sneha.fraud.api.TransactionRequest;
import java.math.BigDecimal;
import java.time.Instant;

final class Txns {
    static final Instant NOW = Instant.parse("2026-11-01T12:00:00Z");

    static TransactionRequest txn(double amount, String category, String country, String device) {
        return new TransactionRequest("t-1", "acct-1", BigDecimal.valueOf(amount), category, country, device, NOW);
    }

    static TransactionRequest txn(double amount) {
        return txn(amount, "groceries", "CA", "dev-1");
    }

    static AccountHistory history(long last10, long last30, double avg, boolean deviceSeen, String lastCountry, Instant lastTime) {
        return new AccountHistory(last10, last30, avg, deviceSeen, lastCountry, lastTime);
    }

    static AccountHistory normal() {
        return history(0, 40, 80.0, true, "CA", NOW.minusSeconds(6 * 3600));
    }

    private Txns() {
    }
}
