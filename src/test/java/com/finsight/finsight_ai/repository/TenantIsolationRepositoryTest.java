package com.finsight.finsight_ai.repository;

import com.finsight.finsight_ai.TestcontainersConfiguration;
import com.finsight.finsight_ai.entity.Account;
import com.finsight.finsight_ai.entity.AccountType;
import com.finsight.finsight_ai.entity.Category;
import com.finsight.finsight_ai.entity.Transaction;
import com.finsight.finsight_ai.entity.TransactionType;
import com.finsight.finsight_ai.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class TenantIsolationRepositoryTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void scopedLookupsNeverReturnAnotherTenantsResources() {
        User userA = user("a@example.com");
        User userB = user("b@example.com");
        Account accountA = account(userA, "A");
        Account accountB = account(userB, "B");
        Category global = category(null, "Global");
        Category categoryA = category(userA, "Private A");
        Category categoryB = category(userB, "Private B");
        Transaction transactionA = transaction(accountA, categoryA, LocalDate.of(2026, 7, 1));
        Transaction transactionB = transaction(accountB, categoryB, LocalDate.of(2026, 7, 2));
        entityManager.flush();
        entityManager.clear();

        assertThat(accountRepository.findByIdAndUserId(accountA.getId(), userA.getId())).isPresent();
        assertThat(accountRepository.findByIdAndUserId(accountB.getId(), userA.getId())).isEmpty();
        assertThat(transactionRepository.findByIdAndUserIdWithAccountJoin(transactionA.getId(), userA.getId()))
                .isPresent();
        assertThat(transactionRepository.findByIdAndUserIdWithAccountJoin(transactionB.getId(), userA.getId()))
                .isEmpty();
        assertThat(categoryRepository.findAccessibleById(global.getId(), userA.getId())).isPresent();
        assertThat(categoryRepository.findAccessibleById(categoryA.getId(), userA.getId())).isPresent();
        assertThat(categoryRepository.findAccessibleById(categoryB.getId(), userA.getId())).isEmpty();
    }

    @Test
    void pagingIsTenantScopedAndOrderedByTransactionDate() {
        User userA = user("page-a@example.com");
        User userB = user("page-b@example.com");
        Account accountA = account(userA, "A");
        Account accountB = account(userB, "B");
        Transaction olderA = transaction(accountA, null, LocalDate.of(2026, 7, 1));
        Transaction newerA = transaction(accountA, null, LocalDate.of(2026, 7, 2));
        transaction(accountB, null, LocalDate.of(2026, 7, 3));
        entityManager.flush();
        entityManager.clear();

        var page = transactionRepository.findAllByAccountUserId(
                userA.getId(),
                PageRequest.of(0, 20, Sort.by(
                        Sort.Order.desc("transactionDate"),
                        Sort.Order.desc("id")
                ))
        );

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(Transaction::getId)
                .containsExactly(newerA.getId(), olderA.getId());
        assertThat(page.getContent()).allSatisfy(transaction ->
                assertThat(transaction.getAccount().getUser().getId()).isEqualTo(userA.getId()));
    }

    @Test
    void softDeletedTransactionIsHiddenAndVersionedDeleteExecutes() {
        User user = user("delete@example.com");
        Account account = account(user, "Delete account");
        Transaction transaction = transaction(account, null, LocalDate.of(2026, 7, 1));
        entityManager.flush();
        UUID transactionId = transaction.getId();

        transactionRepository.delete(transaction);
        entityManager.flush();
        entityManager.clear();

        assertThat(transactionRepository.findByIdAndUserIdWithAccountJoin(transactionId, user.getId())).isEmpty();
        Boolean markedDeleted = jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM transactions WHERE id = ?",
                Boolean.class,
                transactionId
        );
        assertThat(markedDeleted).isTrue();
    }

    @Test
    void databaseRejectsNonPositiveTransactionAmount() {
        User user = user("constraint@example.com");
        Account account = account(user, "Constraint account");
        entityManager.flush();

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO transactions (
                    id, account_id, category_id, amount, description,
                    transaction_date, transaction_type, created_at, updated_at, version
                ) VALUES (?, ?, NULL, 0, NULL, CURRENT_DATE, 'EXPENSE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """, UUID.randomUUID(), account.getId()));
    }

    private User user(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("test-hash");
        user.setDisplayName(email);
        return userRepository.save(user);
    }

    private Account account(User user, String name) {
        Account account = new Account();
        account.setUser(user);
        account.setName(name);
        account.setType(AccountType.CHECKING);
        account.setCurrency("INR");
        account.setBalance(new BigDecimal("1000.00"));
        return accountRepository.save(account);
    }

    private Category category(User user, String name) {
        Category category = new Category();
        category.setUser(user);
        category.setName(name);
        category.setType(TransactionType.EXPENSE);
        return categoryRepository.save(category);
    }

    private Transaction transaction(Account account, Category category, LocalDate date) {
        Transaction transaction = new Transaction();
        transaction.setAccount(account);
        transaction.setCategory(category);
        transaction.setAmount(new BigDecimal("10.00"));
        transaction.setDescription("fixture");
        transaction.setTransactionDate(date);
        transaction.setTransactionType(TransactionType.EXPENSE);
        return transactionRepository.save(transaction);
    }
}
