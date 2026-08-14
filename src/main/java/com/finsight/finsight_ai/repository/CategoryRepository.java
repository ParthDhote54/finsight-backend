package com.finsight.finsight_ai.repository;

import com.finsight.finsight_ai.entity.Category;
import com.finsight.finsight_ai.entity.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID>, JpaSpecificationExecutor<Category> {

    // You still need this for your create/update checks
    boolean existsByNameAndType(String CategoryName, TransactionType transactionType);

    boolean existsByNameIgnoreCaseAndTypeAndUserId(
            String categoryName,
            TransactionType transactionType,
            UUID userId
    );

    boolean existsByNameIgnoreCaseAndTypeAndUserIdAndIdNot(
            String categoryName,
            TransactionType transactionType,
            UUID userId,
            UUID categoryId
    );

    // This is a brilliant custom query for your update logic!
    @Query("SELECT COUNT(c) > 0 FROM Category c WHERE c.name = :categoryName AND c.type = :transactionType AND (c.user.id = :userId OR c.user IS NULL)")
    boolean existsByNameAndTypeAndUserIdOrSystem(
            @Param("categoryName") String categoryName,
            @Param("transactionType") TransactionType transactionType,
            @Param("userId") UUID userId
    );

    List<Category> findByUserId(UUID userId);

    Optional<Category> findByName(String name);

    List<Category> findByUserIdIsNull();

    Optional<Category>findByNameIgnoreCase(String name);

    Optional<Category> findByIdAndUserId(UUID categoryId, UUID userId);

    @Query("SELECT c FROM Category c LEFT JOIN c.user u WHERE c.id = :categoryId AND (u.id = :userId OR u IS NULL)")
    Optional<Category> findAccessibleById(
            @Param("categoryId") UUID categoryId,
            @Param("userId") UUID userId
    );
}
