package com.sneha.fraud.rules;

import com.sneha.fraud.api.TransactionRequest;
import java.util.Optional;

/** One fraud signal. Rules are independent (strategy pattern), so adding a rule is adding one class. */
public interface FraudRule {
    Optional<RuleResult> evaluate(TransactionRequest txn, AccountHistory history);
}
