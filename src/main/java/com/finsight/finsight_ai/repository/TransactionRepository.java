package com.finsight.finsight_ai.repository;

import com.finsight.finsight_ai.entity.Transaction;
import com.finsight.finsight_ai.entity.TransactionType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    @EntityGraph(attributePaths = {"account", "category"})
    List<Transaction> findAllByAccountIdAndTransactionDateBetweenOrderByTransactionDateDesc(UUID accountId, LocalDate startDate, LocalDate endDate);

    List<Transaction> findAllByAccountIdAndCategoryId(UUID accountId, UUID categoryId);

    List<Transaction> findAllByAccountIdAndTransactionType(UUID accountId, TransactionType type);

    List<Transaction> findAllByAccountUserIdAndTransactionType(UUID userId, TransactionType type);

    @EntityGraph(attributePaths = {"account", "category"})
    Page<Transaction> findAllByAccountUserId(UUID userId, Pageable pageable);

    // FIX: Using a.user.id to traverse Account -> User relationship
    @Query("""
        SELECT t FROM Transaction t
        JOIN FETCH t.account a
        LEFT JOIN FETCH t.category c
        WHERE t.id = :transactionId
        AND a.user.id = :userId
        AND t.deletedAt IS NULL
        AND a.deletedAt IS NULL
    """)
    Optional<Transaction> findByIdAndUserIdWithAccountJoin(
            @Param("transactionId") UUID transactionId,
            @Param("userId") UUID userId
    );

    // FIX: Using a.user.id to traverse Account -> User relationship
    @Query("""
        SELECT t FROM Transaction t
        JOIN t.account a
        WHERE a.user.id = :userId
        AND (:searchTerm IS NULL OR LOWER(t.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')))
        AND t.deletedAt IS NULL
        AND a.deletedAt IS NULL
    """)
    List<Transaction> findByUserIdAndSearchCriteria(
            @Param("userId") UUID userId,
            @Param("searchTerm") String searchTerm,
            Pageable pageable
    );
}
