package com.sneha.fraud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FraudScoringApplication {
    public static void main(String[] args) {
        SpringApplication.run(FraudScoringApplication.class, args);
    }
}
