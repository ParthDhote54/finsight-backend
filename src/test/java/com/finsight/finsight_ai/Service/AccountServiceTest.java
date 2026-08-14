package com.finsight.finsight_ai.Service;

import com.finsight.finsight_ai.dto.AccountRequest;
import com.finsight.finsight_ai.entity.Account;
import com.finsight.finsight_ai.entity.AccountType;
import com.finsight.finsight_ai.entity.User;
import com.finsight.finsight_ai.exception.ResourceNotFoundException;
import com.finsight.finsight_ai.repository.AccountRepository;
import com.finsight.finsight_ai.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private UserRepository userRepository;

    private AccountService service;

    @BeforeEach
    void setUp() {
        service = new AccountService(accountRepository, userRepository);
    }

    @Test
    void balanceLookupCannotRevealAnotherUsersAccount() {
        when(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getAccountBalance(ACCOUNT_ID, USER_ID));

        verify(accountRepository).findByIdAndUserId(ACCOUNT_ID, USER_ID);
    }

    @Test
    void accountCreationAssignsAuthenticatedUser() {
        User user = new User();
        user.setId(USER_ID);
        AccountRequest request = new AccountRequest();
        request.setAccountName("Primary");
        request.setAccountType(AccountType.CHECKING);
        request.setCurrency("INR");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        service.createAccount(USER_ID, request);

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        assertSame(user, captor.getValue().getUser());
        assertEquals("Primary", captor.getValue().getName());
        assertEquals(0, BigDecimal.ZERO.compareTo(captor.getValue().getBalance()));
    }
}
