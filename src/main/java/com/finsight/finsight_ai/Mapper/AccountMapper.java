package com.finsight.finsight_ai.Mapper;

import com.finsight.finsight_ai.dto.AccountResponse;
import com.finsight.finsight_ai.entity.Account;


public class AccountMapper {
    //we make this method 'static' so we don't have to create new instance of the mapper every time to use it.

    public static AccountResponse toResponse(Account account) {
        if(account == null) {
            return null;
        }

        return AccountResponse.builder()
                .id(account.getId())
                .accountName(account.getName())
                .accountType(account.getType())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .createdAt(account.getCreatedAt())
                .build();
    }
}
