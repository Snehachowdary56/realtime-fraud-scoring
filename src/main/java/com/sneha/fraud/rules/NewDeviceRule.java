package com.sneha.fraud.rules;

import com.sneha.fraud.api.TransactionRequest;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** A device this account has never used. Weak on its own, strong combined with other signals. */
@Component
@Order(3)
public class NewDeviceRule implements FraudRule {
    @Override
    public Optional<RuleResult> evaluate(TransactionRequest txn, AccountHistory h) {
        if (!h.isNewAccount() && !h.deviceSeenBefore()) {
            return Optional.of(new RuleResult("NEW_DEVICE", 20, "first transaction from device " + txn.deviceId()));
        }
        return Optional.empty();
    }
}
