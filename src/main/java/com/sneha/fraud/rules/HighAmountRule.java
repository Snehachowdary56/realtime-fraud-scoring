package com.sneha.fraud.rules;

import com.sneha.fraud.api.TransactionRequest;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Amount far above what this account normally spends. */
@Component
@Order(1)
public class HighAmountRule implements FraudRule {
    static final double MULTIPLIER = 5.0;
    static final double MIN_AMOUNT = 500.0;
    static final double NEW_ACCOUNT_LIMIT = 3000.0;
    static final int MIN_HISTORY = 5;

    @Override
    public Optional<RuleResult> evaluate(TransactionRequest txn, AccountHistory h) {
        double amount = txn.amount().doubleValue();
        if (h.countLast30Days() >= MIN_HISTORY) {
            if (amount >= MIN_AMOUNT && amount > MULTIPLIER * h.avgAmount30Days()) {
                return Optional.of(new RuleResult("HIGH_AMOUNT", 40, String.format(
                        "amount %.2f is %.1fx the 30-day average %.2f", amount, amount / h.avgAmount30Days(), h.avgAmount30Days())));
            }
        } else if (amount >= NEW_ACCOUNT_LIMIT) {
            return Optional.of(new RuleResult("HIGH_AMOUNT", 30,
                    String.format("amount %.2f on an account with little history", amount)));
        }
        return Optional.empty();
    }
}
