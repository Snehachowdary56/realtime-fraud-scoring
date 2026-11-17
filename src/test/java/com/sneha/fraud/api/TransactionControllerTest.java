package com.sneha.fraud.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sneha.fraud.domain.Decision;
import com.sneha.fraud.service.ScoringService;
import com.sneha.fraud.service.TransactionRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TransactionController.class)
class TransactionControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockBean
    private ScoringService scoring;
    @MockBean
    private TransactionRepository repository;

    private static final String VALID = """
            {"txnId":"t-1","accountId":"acct-1","amount":120.50,"merchantCategory":"groceries",
             "country":"CA","deviceId":"dev-1","timestamp":"2026-11-01T12:00:00Z"}""";

    @Test
    void scoresValidTransaction() throws Exception {
        when(scoring.score(any())).thenReturn(new ScoreResponse("t-1", "acct-1", 0, Decision.ALLOW, List.of()));
        mvc.perform(post("/api/transactions/score").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("ALLOW"))
                .andExpect(jsonPath("$.score").value(0));
    }

    @Test
    void rejectsInvalidTransaction() throws Exception {
        String bad = VALID.replace("120.50", "-5").replace("\"CA\"", "\"Canada\"");
        mvc.perform(post("/api/transactions/score").contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.amount").exists())
                .andExpect(jsonPath("$.fields.country").exists());
    }
}
