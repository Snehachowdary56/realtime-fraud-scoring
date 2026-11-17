package com.sneha.fraud.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sneha.fraud.api.TransactionRequest;
import com.sneha.fraud.domain.Decision;
import com.sneha.fraud.domain.Transaction;
import com.sneha.fraud.service.TransactionRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/**
 * End-to-end: stream labelled transactions through Kafka, let the service score them in real time,
 * read the decisions back from Kafka, and measure detection quality and latency.
 */
class KafkaPipelineIT extends IntegrationTestBase {
    @Autowired
    private TransactionRepository repository;
    @Autowired
    private ObjectMapper json;
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrap;
    @Value("${fraud.topics.transactions}")
    private String inTopic;
    @Value("${fraud.topics.decisions}")
    private String outTopic;

    @Test
    void streamsLabelledTrafficAndCatchesPlantedFraud() throws Exception {
        String run = UUID.randomUUID().toString().substring(0, 8);
        var stream = new SyntheticStream(42, 100, Instant.parse("2026-10-15T08:00:00Z"), run);

        // 30 days of history goes straight into MySQL
        repository.saveAll(stream.history.stream()
                .map(t -> Transaction.scored(t, 0, Decision.ALLOW, List.of())).toList());

        Map<String, Long> sentAt = new HashMap<>();
        Map<String, Long> latencyMs = new HashMap<>();
        Map<String, String> decisions = new HashMap<>();
        Map<String, Long> receivedAt = new HashMap<>();
        receivedNanos = receivedAt;
        List<Double> singleLatencyMs = new ArrayList<>();
        double burstSeconds;

        try (var consumer = consumer(); var producer = producer()) {
            consumer.subscribe(List.of(outTopic));
            // earliest + a fresh group: no decision is missed; old runs are filtered out by txnId
            long burstStart = System.nanoTime();
            for (var l : stream.live) {
                TransactionRequest t = l.txn();
                sentAt.put(t.txnId(), System.nanoTime());
                producer.send(new ProducerRecord<>(inTopic, t.accountId(), json.writeValueAsString(t)));
            }
            producer.flush();

            long deadline = System.currentTimeMillis() + 120_000;
            while (decisions.size() < stream.live.size() && System.currentTimeMillis() < deadline) {
                for (var rec : consumer.poll(Duration.ofMillis(200))) {
                    JsonNode node = json.readTree(rec.value());
                    String id = node.get("txnId").asText();
                    if (sentAt.containsKey(id)) {
                        decisions.put(id, node.get("decision").asText());
                        long now = System.nanoTime();
                        latencyMs.put(id, (now - sentAt.get(id)) / 1_000_000);
                        receivedAt.put(id, now);
                    }
                }
            }
            burstSeconds = (lastDecisionNanos(latencyMs, sentAt) - burstStart) / 1e9;

            // Latency probe: one transaction at a time, waiting for each decision (no queue in front of it)
            for (int i = 0; i < 50; i++) {
                var t = new TransactionRequest("probe-" + run + "-" + i, "probe-" + run + "-" + (i % 5),
                        java.math.BigDecimal.valueOf(20 + i), "groceries", "CA", "probe-dev",
                        Instant.parse("2026-11-01T00:00:00Z").plus(Duration.ofMinutes(30L * i)));
                long t0 = System.nanoTime();
                producer.send(new ProducerRecord<>(inTopic, t.accountId(), json.writeValueAsString(t))).get();
                boolean got = false;
                long until = System.currentTimeMillis() + 10_000;
                while (!got && System.currentTimeMillis() < until) {
                    for (var rec : consumer.poll(Duration.ofMillis(5))) {
                        if (json.readTree(rec.value()).get("txnId").asText().equals(t.txnId())) {
                            singleLatencyMs.add((System.nanoTime() - t0) / 1_000_000.0);
                            got = true;
                        }
                    }
                }
            }
        }
        assertThat(decisions).hasSize(stream.live.size());

        int tp = 0, fn = 0, fp = 0, tn = 0;
        Map<String, int[]> byPattern = new HashMap<>();
        for (var l : stream.live) {
            if (!SyntheticStream.scored(l)) continue;
            boolean flagged = !decisions.get(l.txn().txnId()).equals("ALLOW");
            if (l.fraud()) {
                byPattern.computeIfAbsent(l.pattern(), k -> new int[2])[flagged ? 0 : 1]++;
                if (flagged) tp++; else fn++;
            } else {
                if (flagged) fp++; else tn++;
            }
        }
        Collections.sort(singleLatencyMs);
        double p50 = singleLatencyMs.get(singleLatencyMs.size() / 2);
        double p95 = singleLatencyMs.get((int) Math.ceil(singleLatencyMs.size() * 0.95) - 1);
        double throughput = stream.live.size() / burstSeconds;
        double recall = tp / (double) (tp + fn);
        double fpr = fp / (double) (fp + tn);
        double precision = tp / (double) Math.max(1, tp + fp);

        var report = new StringBuilder()
                .append("## Real-time fraud scoring: end-to-end run\n\n")
                .append("| Metric | Result |\n|---|---|\n")
                .append("| Live transactions streamed through Kafka | ").append(stream.live.size()).append(" |\n")
                .append("| History rows in MySQL | ").append(stream.history.size()).append(" |\n")
                .append("| Planted fraud caught (recall) | ").append(tp).append(" / ").append(tp + fn)
                .append(String.format(" (%.1f%%)", 100 * recall)).append(" |\n")
                .append("| Precision | ").append(String.format("%.1f%%", 100 * precision)).append(" |\n")
                .append("| Normal transactions flagged (false positive rate) | ").append(fp).append(" / ").append(fp + tn)
                .append(String.format(" (%.2f%%)", 100 * fpr)).append(" |\n")
                .append(String.format("| Throughput (all %d sent at once, single consumer) | %.0f transactions/second |%n", stream.live.size(), throughput))
                .append(String.format("| Latency Kafka in to decision out, one at a time (50 events), median | %.1f ms |%n", p50))
                .append(String.format("| Latency Kafka in to decision out, one at a time (50 events), p95 | %.1f ms |%n%n", p95))
                .append("Caught by pattern: ");
        byPattern.forEach((k, v) -> report.append(k).append(" ").append(v[0]).append("/").append(v[0] + v[1]).append("; "));
        report.append("\n");
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/detection-report.md"), report.toString());
        System.out.println(report);

        assertThat(singleLatencyMs).hasSize(50);
        assertThat(recall).isGreaterThanOrEqualTo(0.95);
        assertThat(fpr).isLessThanOrEqualTo(0.03);
    }

    private Map<String, Long> receivedNanos;

    private long lastDecisionNanos(Map<String, Long> latency, Map<String, Long> sent) {
        return receivedNanos.values().stream().mapToLong(Long::longValue).max().orElse(System.nanoTime());
    }

    private KafkaConsumer<String, String> consumer() {
        var p = new Properties();
        p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        p.put(ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID());
        p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(p);
    }

    private KafkaProducer<String, String> producer() {
        var p = new Properties();
        p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new KafkaProducer<>(p);
    }
}
