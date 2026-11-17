package com.sneha.fraud.service;

import com.sneha.fraud.domain.Transaction;
import com.sneha.fraud.rules.AccountHistory;
import com.sneha.fraud.rules.StructuringRule;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Builds an account's history from MySQL using only the transactions before the one being scored. */
@Service
public class HistoryService {
    private final TransactionRepository repository;

    public HistoryService(TransactionRepository repository) {
        this.repository = repository;
    }

    public AccountHistory historyBefore(String accountId, String deviceId, Instant at) {
        long last10 = repository.countInWindow(accountId, at.minus(Duration.ofMinutes(10)), at);
        Instant monthAgo = at.minus(Duration.ofDays(30));
        long last30d = repository.countInWindow(accountId, monthAgo, at);
        Double avgOrNull = last30d == 0 ? null : repository.averageAmountInWindow(accountId, monthAgo, at);
        double avg = avgOrNull == null ? 0 : avgOrNull;
        boolean deviceSeen = repository.existsByAccountIdAndDeviceIdAndCreatedAtBefore(accountId, deviceId, at);
        Optional<Transaction> last = repository.findFirstByAccountIdAndCreatedAtBeforeOrderByCreatedAtDesc(accountId, at);
        long nearLimit = repository.countAmountRangeInWindow(accountId, at.minus(Duration.ofHours(24)), at,
                StructuringRule.LOW, StructuringRule.REPORTING_LIMIT);
        return new AccountHistory(last10, last30d, avg, deviceSeen,
                last.map(Transaction::getCountry).orElse(null),
                last.map(Transaction::getCreatedAt).orElse(null), nearLimit);
    }
}
