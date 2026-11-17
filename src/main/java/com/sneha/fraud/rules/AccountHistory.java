package com.sneha.fraud.rules;

import java.time.Instant;

/**
 * What we know about an account before the current transaction.
 *
 * @param countLast10Min   transactions in the 10 minutes before this one
 * @param countLast30Days  transactions in the 30 days before this one
 * @param avgAmount30Days  average amount over those 30 days (0 if none)
 * @param deviceSeenBefore whether this device was used by the account before
 * @param lastCountry      country of the previous transaction (null if none)
 * @param lastTime         time of the previous transaction (null if none)
 */
public record AccountHistory(long countLast10Min, long countLast30Days, double avgAmount30Days,
                             boolean deviceSeenBefore, String lastCountry, Instant lastTime) {

    public static AccountHistory empty() {
        return new AccountHistory(0, 0, 0, false, null, null);
    }

    public boolean isNewAccount() {
        return countLast30Days == 0;
    }
}
