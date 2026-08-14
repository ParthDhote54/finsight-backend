package com.finsight.finsight_ai.repository;


import com.finsight.finsight_ai.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    //fetch the user's dashboard.
    List<Account> findAllByUserId(UUID userID);

    //the security check to enforce security such that no one can know the account balance only if he access the userBalance.
    Optional<Account> findByIdAndUserId(UUID id, UUID userID);


}
