package com.finsight.finsight_ai.transaction.adapter;


import com.finsight.finsight_ai.transaction.port.TransactionCategoryUpdatePort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.swing.text.html.parser.Entity;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
@Component
@RequiredArgsConstructor
@Slf4j
public class transactionCategoryUpdateAdapter implements TransactionCategoryUpdatePort {

    private final EntityManager entityManager;
    @Override
    @Transactional
    public boolean updateCategoryIfNull(UUID transactionId, UUID categoryId) {
        String sql = """
                UPDATE transactions
                SET category_id = :categoryId
                WHERE id =:transactionId 
                AND category_id IS NULL """;

        Query query = entityManager.createNativeQuery(sql)
                .setParameter("categoryId", categoryId)
                .setParameter("transactionId", transactionId);


        int rowsUpdated = query.executeUpdate();

        if(rowsUpdated == 0) {
            log.warn("event = OPTIMISTIC_LOCKING_PREVENTED_UPDATE | transactionId = {}", transactionId);
            return false;
        }

        return true;
    }
}
