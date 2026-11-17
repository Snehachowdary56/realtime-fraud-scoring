package com.sneha.fraud.rules;

import com.sneha.fraud.api.TransactionRequest;
import java.util.Optional;
import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Merchant categories where stolen money is easy to cash out. */
@Component
@Order(5)
public class HighRiskMerchantRule implements FraudRule {
    static final Set<String> HIGH_RISK = Set.of("gift_cards", "crypto", "wire_transfer");

    @Override
    public Optional<RuleResult> evaluate(TransactionRequest txn, AccountHistory h) {
        if (HIGH_RISK.contains(txn.merchantCategory())) {
            return Optional.of(new RuleResult("HIGH_RISK_MERCHANT", 15, "high-risk category " + txn.merchantCategory()));
        }
        return Optional.empty();
    }
}
