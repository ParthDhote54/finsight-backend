package com.finsight.finsight_ai.validation;

import com.finsight.finsight_ai.Controller.AccountController;
import com.finsight.finsight_ai.Controller.CategoryController;
import com.finsight.finsight_ai.Controller.TransactionController;
import com.finsight.finsight_ai.Controller.UserController;
import com.finsight.finsight_ai.ai.chat.adapters.in.web.ChatController;
import com.finsight.finsight_ai.ai.chat.domain.ChatRequest;
import com.finsight.finsight_ai.dto.AccountRequest;
import com.finsight.finsight_ai.dto.CategoryRequest;
import com.finsight.finsight_ai.dto.LoginRequest;
import com.finsight.finsight_ai.dto.TransactionRequest;
import com.finsight.finsight_ai.dto.UserRegisterationRequest;
import com.finsight.finsight_ai.entity.AccountType;
import com.finsight.finsight_ai.entity.TransactionType;
import com.finsight.finsight_ai.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreRequestValidationTest {

    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = VALIDATOR_FACTORY.getValidator();

    @AfterAll
    static void closeValidatorFactory() {
        VALIDATOR_FACTORY.close();
    }

    @Test
    void allWriteRequestBodiesActivateBeanValidation() {
        assertValidatedBody(UserController.class, "registerUser", UserRegisterationRequest.class);
        assertValidatedBody(UserController.class, "loginUser", LoginRequest.class);
        assertValidatedBody(AccountController.class, "createAccount", AccountRequest.class);
        assertValidatedBody(CategoryController.class, "createCategory", CategoryRequest.class);
        assertValidatedBody(CategoryController.class, "updateCategory", CategoryRequest.class);
        assertValidatedBody(TransactionController.class, "createTransaction", TransactionRequest.class);
        assertValidatedBody(TransactionController.class, "updateTransaction", TransactionRequest.class);
        assertValidatedBody(ChatController.class, "processChat", ChatRequest.class);
    }

    @Test
    void everyAuthenticatedControllerBindsUserPrincipalExplicitly() {
        for (Class<?> controller : List.of(
                AccountController.class,
                CategoryController.class,
                TransactionController.class,
                ChatController.class
        )) {
            for (Method method : controller.getDeclaredMethods()) {
                for (Parameter parameter : method.getParameters()) {
                    if (parameter.getType() == UserPrincipal.class) {
                        assertTrue(parameter.isAnnotationPresent(AuthenticationPrincipal.class),
                                () -> controller.getSimpleName() + "." + method.getName()
                                        + " must bind UserPrincipal with @AuthenticationPrincipal");
                    }
                }
            }
        }
    }

    @Test
    void transactionRequestRejectsMissingAndInvalidFinancialFields() {
        TransactionRequest request = new TransactionRequest();
        assertTrue(fields(request).containsAll(Set.of("accountId", "amount", "transactionType")));

        request.setAccountId(java.util.UUID.randomUUID());
        request.setTransactionType(TransactionType.EXPENSE);
        request.setAmount(BigDecimal.ZERO);
        assertTrue(fields(request).contains("amount"));

        request.setAmount(new BigDecimal("-0.01"));
        assertTrue(fields(request).contains("amount"));

        request.setAmount(new BigDecimal("1.00001"));
        assertTrue(fields(request).contains("amount"));
    }

    @Test
    void userAccountAndCategoryRequestsRejectMissingRequiredFields() {
        UserRegisterationRequest registration = new UserRegisterationRequest();
        assertTrue(fields(registration).containsAll(Set.of("Email", "rawPassword", "displayName")));

        LoginRequest login = new LoginRequest();
        assertTrue(fields(login).containsAll(Set.of("email", "rawPassword")));

        AccountRequest account = new AccountRequest();
        account.setAccountName(" ");
        account.setAccountType(AccountType.CHECKING);
        account.setCurrency("IN");
        assertTrue(fields(account).containsAll(Set.of("accountName", "currency")));

        CategoryRequest category = CategoryRequest.builder().categoryName(" ").build();
        assertTrue(fields(category).containsAll(Set.of("categoryName", "categoryType")));
    }

    private void assertValidatedBody(Class<?> controller, String methodName, Class<?> bodyType) {
        Method method = List.of(controller.getDeclaredMethods()).stream()
                .filter(candidate -> candidate.getName().equals(methodName))
                .filter(candidate -> List.of(candidate.getParameterTypes()).contains(bodyType))
                .findFirst()
                .orElseThrow();
        Parameter body = List.of(method.getParameters()).stream()
                .filter(parameter -> parameter.getType() == bodyType)
                .findFirst()
                .orElseThrow();

        assertTrue(body.isAnnotationPresent(RequestBody.class));
        assertTrue(body.isAnnotationPresent(Valid.class));
    }

    private Set<String> fields(Object request) {
        return VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
