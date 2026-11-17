package com.sneha.fraud.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.Instant;

/** One card transaction to score. timestamp is the event time, so scoring is repeatable. */
public record TransactionRequest(
        @NotBlank String txnId,
        @NotBlank String accountId,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotBlank String merchantCategory,
        @NotBlank @Pattern(regexp = "[A-Z]{2}") String country,
        @NotBlank String deviceId,
        @NotNull Instant timestamp) {
}
