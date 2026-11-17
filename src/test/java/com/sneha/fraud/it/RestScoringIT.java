package com.sneha.fraud.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.sneha.fraud.api.ScoreResponse;
import com.sneha.fraud.api.TransactionRequest;
import com.sneha.fraud.domain.Decision;
import com.sneha.fraud.service.TransactionRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

class RestScoringIT extends IntegrationTestBase {
    @Autowired
    private TestRestTemplate http;
    @Autowired
    private TransactionRepository repository;

    private final String run = UUID.randomUUID().toString().substring(0, 8);
    private final Instant start = Instant.parse("2026-10-01T09:00:00Z");

    private ScoreResponse post(String account, int n, double amount, String country, String device, Instant at) {
        var req = new TransactionRequest(run + "-" + account + "-" + n, run + "-" + account,
                BigDecimal.valueOf(amount), "groceries", country, device, at);
        var res = http.postForEntity("/api/transactions/score", req, ScoreResponse.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return res.getBody();
    }

    @Test
    void historyFromMySqlDrivesTheDecision() {
        for (int i = 0; i < 6; i++) {
            assertThat(post("a", i, 50 + i, "CA", "phone", start.plus(Duration.ofHours(6L * i))).decision())
                    .isEqualTo(Decision.ALLOW);
        }
        // 12x the average, from a new device: review-level risk from history stored in MySQL
        ScoreResponse big = post("a", 99, 650, "CA", "laptop", start.plus(Duration.ofDays(2)));
        assertThat(big.decision()).isEqualTo(Decision.REVIEW);
        assertThat(big.reasons()).anyMatch(r -> r.startsWith("HIGH_AMOUNT")).anyMatch(r -> r.startsWith("NEW_DEVICE"));
        assertThat(repository.findTop20ByAccountIdOrderByCreatedAtDesc(run + "-a")).hasSize(7);
    }

    @Test
    void velocityBurstIsCaughtOnTheFifthPurchase() {
        Decision last = null;
        for (int i = 0; i < 5; i++) {
            last = post("b", i, 3, "CA", "phone", start.plus(Duration.ofSeconds(30L * i))).decision();
        }
        assertThat(last).isEqualTo(Decision.REVIEW);
    }

    @Test
    void duplicateTransactionIsRejected() {
        post("c", 1, 20, "CA", "phone", start);
        var again = new TransactionRequest(run + "-c-1", run + "-c", BigDecimal.TEN, "groceries", "CA", "phone", start);
        assertThat(http.postForEntity("/api/transactions/score", again, String.class).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }
}
