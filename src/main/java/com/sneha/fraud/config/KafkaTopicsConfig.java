package com.sneha.fraud.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Creates the Kafka topics at startup. Transactions are keyed by account, so one account's events stay in order. */
@Configuration
public class KafkaTopicsConfig {
    @Bean
    NewTopic transactionsTopic(FraudProperties props) {
        return TopicBuilder.name(props.topics().transactions()).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic decisionsTopic(FraudProperties props) {
        return TopicBuilder.name(props.topics().decisions()).partitions(3).replicas(1).build();
    }
}
