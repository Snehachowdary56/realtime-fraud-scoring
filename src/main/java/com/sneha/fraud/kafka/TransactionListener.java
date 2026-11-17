package com.sneha.fraud.kafka;

import com.sneha.fraud.api.ScoreResponse;
import com.sneha.fraud.api.TransactionRequest;
import com.sneha.fraud.config.FraudProperties;
import com.sneha.fraud.service.ScoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Real-time path: read transactions from Kafka, score them, publish the decision. */
@Component
public class TransactionListener {
    private static final Logger log = LoggerFactory.getLogger(TransactionListener.class);
    private final ScoringService scoring;
    private final KafkaTemplate<String, Object> kafka;
    private final String decisionsTopic;

    public TransactionListener(ScoringService scoring, KafkaTemplate<String, Object> kafka, FraudProperties props) {
        this.scoring = scoring;
        this.kafka = kafka;
        this.decisionsTopic = props.topics().decisions();
    }

    @KafkaListener(topics = "${fraud.topics.transactions}")
    public void onTransaction(TransactionRequest txn) {
        if (txn == null) {
            log.warn("Skipped a message that is not a valid transaction");
            return;
        }
        try {
            ScoreResponse result = scoring.score(txn);
            kafka.send(decisionsTopic, txn.accountId(), result);
        } catch (DataIntegrityViolationException duplicate) {
            log.info("Transaction {} was already scored; ignoring the redelivery", txn.txnId());
        }
    }
}
