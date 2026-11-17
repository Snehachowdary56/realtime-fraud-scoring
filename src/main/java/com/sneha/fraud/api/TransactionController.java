package com.sneha.fraud.api;

import com.sneha.fraud.domain.Transaction;
import com.sneha.fraud.service.ScoringService;
import com.sneha.fraud.service.TransactionRepository;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TransactionController {
    private final ScoringService scoring;
    private final TransactionRepository repository;

    public TransactionController(ScoringService scoring, TransactionRepository repository) {
        this.scoring = scoring;
        this.repository = repository;
    }

    /** Synchronous scoring, e.g. for a checkout that must wait for the decision. */
    @PostMapping("/transactions/score")
    public ResponseEntity<ScoreResponse> score(@Valid @RequestBody TransactionRequest request) {
        return ResponseEntity.status(HttpStatus.OK).body(scoring.score(request));
    }

    /** Latest 20 scored transactions for one account, for investigators. */
    @GetMapping("/accounts/{accountId}/transactions")
    public List<ScoreResponse> history(@PathVariable String accountId) {
        return repository.findTop20ByAccountIdOrderByCreatedAtDesc(accountId).stream()
                .map(Transaction::toResponse)
                .toList();
    }
}
