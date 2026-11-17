package com.sneha.fraud.rules;

import com.sneha.fraud.api.TransactionRequest;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Many transactions in a short time, typical of card testing or a takeover. */
@Component
@Order(2)
public class VelocityRule implements FraudRule {
    static final int MAX_IN_10_MIN = 4;

    @Override
    public Optional<RuleResult> evaluate(TransactionRequest txn, AccountHistory h) {
        if (h.countLast10Min() >= MAX_IN_10_MIN) {
            return Optional.of(new RuleResult("VELOCITY", 40,
                    String.format("%d transactions in the previous 10 minutes", h.countLast10Min())));
        }
        return Optional.empty();
    }
}
