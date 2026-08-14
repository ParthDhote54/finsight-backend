package com.finsight.finsight_ai.transaction.port;

import java.util.UUID;

/**
 * this is an Inbound Port.
 * The transaction Domain defines the rule,and the external AI infrastructure must obey it.
 */

public interface TransactionCategoryUpdatePort {

    /*
    *Safely updates a transaction's category only if it hasn't been categorized yet.
    * @param transactionId, the id of the transaction to update.
    * @param categoryId The ID of the AI-predicted category.
    * @return true if updated, false if the transaction was already categorized by the user.

     */

    boolean updateCategoryIfNull(UUID transactionId, UUID categoryId);

}
