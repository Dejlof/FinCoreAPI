package com.fincore.api.repository;

import com.fincore.api.entity.Transaction;
import com.fincore.api.entity.enums.TransactionStatus;
import com.fincore.api.entity.enums.TransactionType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByReference(String reference);

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);
 
    

    List<Transaction> findFirst200ByTypeAndStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
            TransactionType type, TransactionStatus status, Instant createdBefore);


    /**
     * Pessimistic write lock so the same reference can never be settled twice
     * concurrently (e.g. a webhook delivery racing the reconciliation job).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Transaction t WHERE t.reference = :reference")
    Optional<Transaction> findByReferenceForUpdate(@Param("reference") String reference);

    @Query("""
            SELECT t FROM Transaction t
            WHERE t.sourceAccount.id = :accountId OR t.destinationAccount.id = :accountId
            ORDER BY t.createdAt DESC
            """)
    Page<Transaction> findAllByAccountId(@Param("accountId") UUID accountId, Pageable pageable);
}
