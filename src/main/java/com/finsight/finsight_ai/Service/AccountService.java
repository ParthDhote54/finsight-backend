package com.finsight.finsight_ai.Service;


import com.finsight.finsight_ai.Mapper.AccountMapper;
import com.finsight.finsight_ai.dto.AccountRequest;
import com.finsight.finsight_ai.dto.AccountResponse;
import com.finsight.finsight_ai.entity.Account;
import com.finsight.finsight_ai.entity.User;
import com.finsight.finsight_ai.exception.ResourceNotFoundException;
import com.finsight.finsight_ai.repository.AccountRepository;
import com.finsight.finsight_ai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Currency;
import java.util.Locale;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository repository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public String getAccountBalance(UUID accountId, UUID userId) {

        Account account = repository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        return account.getBalance().toString();
    }


    @Transactional
    public AccountResponse createAccount(UUID userId, AccountRequest request){

        validateRequest(request);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Account account = new Account();
        account.setName(request.getAccountName());
        account.setType(request.getAccountType());
        account.setCurrency(normalizeCurrency(request.getCurrency()));
        account.setUser(user);
        repository.save(account);

        return AccountMapper.toResponse(account);
    }

    @Transactional(readOnly = true) //this tells postgres not to prepare for write operations.
    public List<AccountResponse> getAllUserAccounts(UUID userId){
        List<Account>accounts = repository.findAllByUserId(userId);

        return accounts.stream().map(AccountMapper::toResponse).toList();
    }

    private void validateRequest(AccountRequest request) {
        if (request == null
                || request.getAccountName() == null
                || request.getAccountName().isBlank()
                || request.getAccountType() == null
                || request.getCurrency() == null
                || request.getCurrency().isBlank()
                || request.getCurrency().length() != 3) {
            throw new IllegalStateException("Invalid account request");
        }
        normalizeCurrency(request.getCurrency());
    }

    private String normalizeCurrency(String currencyCode) {
        String normalized = currencyCode.trim().toUpperCase(Locale.ROOT);
        try {
            return Currency.getInstance(normalized).getCurrencyCode();
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Unsupported ISO 4217 currency code");
        }
    }
}
