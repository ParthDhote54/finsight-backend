package com.finsight.finsight_ai.repository;

import com.finsight.finsight_ai.entity.Category;
import com.finsight.finsight_ai.entity.TransactionType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public class CategorySpecifications {

    public static Specification<Category>getCategoriesForUser(UUID userId,
                                                              TransactionType type) {

        return ((root, query, criteriaBuilder) -> {

            //1. Predicate: Is it owned by this specific user?
            Predicate belongsToUser = criteriaBuilder.equal(root.get("user").get("id"), userId);

            //2. Predicate : Is it a system category (user is null) ?
            Predicate isSystem = criteriaBuilder.isNull(root.get("user"));


            //3. Combine with OR : (user_id = X or user_id IS NULL)
            Predicate validOwner = criteriaBuilder.or(belongsToUser, isSystem);

            //4.Dynamic Check : Did the frontend provide a type to filter by ?

            if(type != null) {
                //If yes, create a new Predicate for the type.
                Predicate matchesType = criteriaBuilder.equal(root.get("type"), type);


                //Combine the type check AND the owner check.
                return criteriaBuilder.and(matchesType, validOwner);

            }

            return validOwner;
        });
    }
}
