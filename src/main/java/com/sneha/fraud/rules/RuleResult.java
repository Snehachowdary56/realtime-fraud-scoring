package com.sneha.fraud.rules;

/** A rule that fired: how many risk points it adds and a reason an investigator can read. */
public record RuleResult(String rule, int points, String reason) {
}
