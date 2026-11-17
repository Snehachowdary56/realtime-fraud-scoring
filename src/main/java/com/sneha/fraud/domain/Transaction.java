package com.sneha.fraud.domain;

import com.sneha.fraud.api.ScoreResponse;
import com.sneha.fraud.api.TransactionRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "transactions")
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "txn_id", nullable = false, unique = true)
    private String txnId;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "merchant_category", nullable = false)
    private String merchantCategory;

    @Column(nullable = false)
    private String country;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private int score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Decision decision;

    @Column(nullable = false)
    private String reasons;

    protected Transaction() {
    }

    public static Transaction scored(TransactionRequest r, int score, Decision decision, List<String> reasons) {
        Transaction t = new Transaction();
        t.txnId = r.txnId();
        t.accountId = r.accountId();
        t.amount = r.amount();
        t.merchantCategory = r.merchantCategory();
        t.country = r.country();
        t.deviceId = r.deviceId();
        t.createdAt = r.timestamp();
        t.score = score;
        t.decision = decision;
        String joined = String.join("; ", reasons);
        t.reasons = joined.length() > 500 ? joined.substring(0, 500) : joined;
        return t;
    }

    public ScoreResponse toResponse() {
        List<String> list = reasons.isBlank() ? List.of() : Arrays.asList(reasons.split("; "));
        return new ScoreResponse(txnId, accountId, score, decision, list);
    }

    public String getAccountId() { return accountId; }
    public String getCountry() { return country; }
    public Instant getCreatedAt() { return createdAt; }
    public Decision getDecision() { return decision; }
}
