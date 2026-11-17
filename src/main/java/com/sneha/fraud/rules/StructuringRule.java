package com.sneha.fraud.rules;

import com.sneha.fraud.api.TransactionRequest;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Anti-money laundering (AML): "structuring" means splitting money into several amounts just under the
 * $10,000 reporting limit to avoid a report. Fires on the 3rd such transaction within 24 hours.
 */
@Component
@Order(6)
public class StructuringRule implements FraudRule {
    public static final BigDecimal REPORTING_LIMIT = new BigDecimal("10000.00");
    public static final BigDecimal LOW = new BigDecimal("9000.00");
    static final int PRIOR_NEAR_LIMIT = 2;

    @Override
    public Optional<RuleResult> evaluate(TransactionRequest txn, AccountHistory h) {
        boolean nearLimit = txn.amount().compareTo(LOW) >= 0 && txn.amount().compareTo(REPORTING_LIMIT) < 0;
        if (nearLimit && h.nearLimitLast24h() >= PRIOR_NEAR_LIMIT) {
            return Optional.of(new RuleResult("AML_STRUCTURING", 50, String.format(
                    "%d transactions just under $10,000 in 24 hours (possible structuring)", h.nearLimitLast24h() + 1)));
        }
        return Optional.empty();
    }
}
