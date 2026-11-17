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
 * @param nearLimitLast24h transactions of $9,000 to $9,999.99 in the previous 24 hours (structuring signal)
 */
public record AccountHistory(long countLast10Min, long countLast30Days, double avgAmount30Days,
                             boolean deviceSeenBefore, String lastCountry, Instant lastTime,
                             long nearLimitLast24h) {

    public static AccountHistory empty() {
        return new AccountHistory(0, 0, 0, false, null, null, 0);
    }

    public boolean isNewAccount() {
        return countLast30Days == 0;
    }
}
