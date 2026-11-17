package com.sneha.fraud.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sneha.fraud.api.TransactionRequest;
import com.sneha.fraud.config.FraudProperties;
import com.sneha.fraud.domain.Decision;
import com.sneha.fraud.domain.Transaction;
import com.sneha.fraud.rules.AccountHistory;
import com.sneha.fraud.rules.HighAmountRule;
import com.sneha.fraud.rules.HighRiskMerchantRule;
import com.sneha.fraud.rules.ImpossibleTravelRule;
import com.sneha.fraud.rules.NewDeviceRule;
import com.sneha.fraud.rules.VelocityRule;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScoringServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-01T12:00:00Z");
    private HistoryService history;
    private TransactionRepository repository;
    private ScoringService service;

    @BeforeEach
    void setUp() {
        history = mock(HistoryService.class);
        repository = mock(TransactionRepository.class);
        var props = new FraudProperties(new FraudProperties.Topics("t", "d"), new FraudProperties.Thresholds(40, 70));
        service = new ScoringService(List.of(new HighAmountRule(), new VelocityRule(), new NewDeviceRule(),
                new ImpossibleTravelRule(), new HighRiskMerchantRule()), history, repository, props);
    }

    private TransactionRequest txn(double amount, String category, String country, String device) {
        return new TransactionRequest("t-9", "acct-1", BigDecimal.valueOf(amount), category, country, device, NOW);
    }

    @Test
    void normalPurchaseIsAllowedAndSaved() {
        when(history.historyBefore(anyString(), anyString(), any())).thenReturn(
                new AccountHistory(0, 40, 80, true, "CA", NOW.minusSeconds(7200)));
        var result = service.score(txn(75, "groceries", "CA", "dev-1"));
        assertThat(result.decision()).isEqualTo(Decision.ALLOW);
        assertThat(result.score()).isZero();
        verify(repository).save(any(Transaction.class));
    }

    @Test
    void accountTakeoverIsBlockedWithReasons() {
        when(history.historyBefore(anyString(), anyString(), any())).thenReturn(
                new AccountHistory(0, 40, 80, false, "CA", NOW.minusSeconds(15 * 60)));
        var result = service.score(txn(1200, "gift_cards", "NG", "dev-x"));
        assertThat(result.decision()).isEqualTo(Decision.BLOCK);
        assertThat(result.score()).isEqualTo(100); // 40 + 20 + 45 + 15 = 120, capped at 100
        assertThat(result.reasons()).hasSize(4).anyMatch(r -> r.startsWith("IMPOSSIBLE_TRAVEL"));
    }

    @Test
    void singleStrongSignalGoesToReview() {
        when(history.historyBefore(anyString(), anyString(), any())).thenReturn(
                new AccountHistory(5, 40, 80, true, "CA", NOW.minusSeconds(60)));
        var result = service.score(txn(30, "groceries", "CA", "dev-1"));
        assertThat(result.decision()).isEqualTo(Decision.REVIEW);
        assertThat(result.score()).isEqualTo(40);
    }

    @Test
    void thresholdsAreInclusive() {
        assertThat(service.decide(39)).isEqualTo(Decision.ALLOW);
        assertThat(service.decide(40)).isEqualTo(Decision.REVIEW);
        assertThat(service.decide(70)).isEqualTo(Decision.BLOCK);
    }
}
