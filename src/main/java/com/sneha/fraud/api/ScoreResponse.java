package com.sneha.fraud.api;

import com.sneha.fraud.domain.Decision;
import java.util.List;

public record ScoreResponse(String txnId, String accountId, int score, Decision decision, List<String> reasons) {
}
