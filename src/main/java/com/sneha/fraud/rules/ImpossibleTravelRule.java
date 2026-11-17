package com.sneha.fraud.rules;

import com.sneha.fraud.api.TransactionRequest;
import java.time.Duration;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** A different country less than an hour after the previous transaction. */
@Component
@Order(4)
public class ImpossibleTravelRule implements FraudRule {
    static final Duration WINDOW = Duration.ofHours(1);

    @Override
    public Optional<RuleResult> evaluate(TransactionRequest txn, AccountHistory h) {
        if (h.lastCountry() == null || h.lastTime() == null || h.lastCountry().equals(txn.country())) {
            return Optional.empty();
        }
        Duration gap = Duration.between(h.lastTime(), txn.timestamp());
        if (!gap.isNegative() && gap.compareTo(WINDOW) < 0) {
            return Optional.of(new RuleResult("IMPOSSIBLE_TRAVEL", 45, String.format(
                    "%s to %s in %d minutes", h.lastCountry(), txn.country(), gap.toMinutes())));
        }
        return Optional.empty();
    }
}
