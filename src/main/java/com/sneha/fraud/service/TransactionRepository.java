package com.sneha.fraud.service;

import com.sneha.fraud.domain.Transaction;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every query filters by account_id first, so it uses the composite indexes from V1__create_transactions.sql. */
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query("select count(t) from Transaction t where t.accountId = :account and t.createdAt >= :from and t.createdAt < :to")
    long countInWindow(@Param("account") String accountId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("select avg(t.amount) from Transaction t where t.accountId = :account and t.createdAt >= :from and t.createdAt < :to")
    Double averageAmountInWindow(@Param("account") String accountId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("select count(t) from Transaction t where t.accountId = :account and t.createdAt >= :from and t.createdAt < :to"
            + " and t.amount >= :low and t.amount < :high")
    long countAmountRangeInWindow(@Param("account") String accountId, @Param("from") Instant from, @Param("to") Instant to,
                                  @Param("low") java.math.BigDecimal low, @Param("high") java.math.BigDecimal high);

    boolean existsByAccountIdAndDeviceIdAndCreatedAtBefore(String accountId, String deviceId, Instant before);

    Optional<Transaction> findFirstByAccountIdAndCreatedAtBeforeOrderByCreatedAtDesc(String accountId, Instant before);

    List<Transaction> findTop20ByAccountIdOrderByCreatedAtDesc(String accountId);
}
