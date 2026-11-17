package com.sneha.fraud.service;

import com.sneha.fraud.api.ScoreResponse;
import com.sneha.fraud.api.TransactionRequest;
import com.sneha.fraud.config.FraudProperties;
import com.sneha.fraud.domain.Decision;
import com.sneha.fraud.domain.Transaction;
import com.sneha.fraud.rules.AccountHistory;
import com.sneha.fraud.rules.FraudRule;
import com.sneha.fraud.rules.RuleResult;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Scores a transaction: load history, run every rule, add up points, decide, save. */
@Service
public class ScoringService {
    private final List<FraudRule> rules;
    private final HistoryService history;
    private final TransactionRepository repository;
    private final FraudProperties.Thresholds thresholds;

    public ScoringService(List<FraudRule> rules, HistoryService history, TransactionRepository repository, FraudProperties props) {
        this.rules = rules;
        this.history = history;
        this.repository = repository;
        this.thresholds = props.thresholds();
    }

    @Transactional
    public ScoreResponse score(TransactionRequest txn) {
        AccountHistory h = history.historyBefore(txn.accountId(), txn.deviceId(), txn.timestamp());
        List<RuleResult> fired = evaluate(txn, h);
        int score = Math.min(100, fired.stream().mapToInt(RuleResult::points).sum());
        Decision decision = decide(score);
        List<String> reasons = fired.stream().map(r -> r.rule() + ": " + r.reason()).toList();
        repository.save(Transaction.scored(txn, score, decision, reasons));
        return new ScoreResponse(txn.txnId(), txn.accountId(), score, decision, reasons);
    }

    List<RuleResult> evaluate(TransactionRequest txn, AccountHistory h) {
        return rules.stream().map(rule -> rule.evaluate(txn, h)).flatMap(Optional::stream).toList();
    }

    Decision decide(int score) {
        if (score >= thresholds.block()) return Decision.BLOCK;
        if (score >= thresholds.review()) return Decision.REVIEW;
        return Decision.ALLOW;
    }
}
