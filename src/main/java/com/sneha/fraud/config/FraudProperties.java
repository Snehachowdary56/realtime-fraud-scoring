package com.sneha.fraud.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fraud")
public record FraudProperties(Topics topics, Thresholds thresholds) {
    public record Topics(String transactions, String decisions) {}
    public record Thresholds(int review, int block) {}
}
