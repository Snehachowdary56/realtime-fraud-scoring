package com.sneha.fraud.it;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration tests run against a real MySQL and a real Kafka broker
 * (GitHub Actions service containers, or docker compose locally): mvn -Pintegration test
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class IntegrationTestBase {
}
