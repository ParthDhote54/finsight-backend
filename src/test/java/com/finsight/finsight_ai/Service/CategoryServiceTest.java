package com.finsight.finsight_ai.Service;

import com.finsight.finsight_ai.dto.CategoryRequest;
import com.finsight.finsight_ai.entity.Category;
import com.finsight.finsight_ai.entity.TransactionType;
import com.finsight.finsight_ai.entity.User;
import com.finsight.finsight_ai.exception.ResourceNotFoundException;
import com.finsight.finsight_ai.repository.CategoryRepository;
import com.finsight.finsight_ai.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private UserRepository userRepository;

    private CategoryService service;

    @BeforeEach
    void setUp() {
        service = new CategoryService(categoryRepository, userRepository);
    }

    @Test
    void userCreatedCategoryIsOwnedByAuthenticatedUser() {
        User user = new User();
        user.setId(USER_ID);
        CategoryRequest request = CategoryRequest.builder()
                .categoryName("Coffee")
                .categoryType(TransactionType.EXPENSE)
                .build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(categoryRepository.existsByNameIgnoreCaseAndTypeAndUserId("Coffee", TransactionType.EXPENSE, USER_ID))
                .thenReturn(false);

        service.createCategory(request, USER_ID);

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());
        assertSame(user, captor.getValue().getUser());
        assertEquals("Coffee", captor.getValue().getName());
    }

    @Test
    void duplicateCheckIsScopedToAuthenticatedUser() {
        User user = new User();
        user.setId(USER_ID);
        CategoryRequest request = CategoryRequest.builder()
                .categoryName("Coffee")
                .categoryType(TransactionType.EXPENSE)
                .build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(categoryRepository.existsByNameIgnoreCaseAndTypeAndUserId("Coffee", TransactionType.EXPENSE, USER_ID))
                .thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.createCategory(request, USER_ID));

        verify(categoryRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void anotherUsersOrGlobalCategoryCannotBeUpdatedThroughUserRoute() {
        UUID categoryId = UUID.randomUUID();
        CategoryRequest request = CategoryRequest.builder()
                .categoryName("Private")
                .categoryType(TransactionType.EXPENSE)
                .build();
        when(categoryRepository.findByIdAndUserId(categoryId, USER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.updateCategory(categoryId, request, USER_ID));

        verify(categoryRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void categoryTypeCannotBeChangedAfterCreation() {
        UUID categoryId = UUID.randomUUID();
        Category existing = new Category();
        existing.setId(categoryId);
        existing.setType(TransactionType.EXPENSE);
        existing.setName("Coffee");
        CategoryRequest request = CategoryRequest.builder()
                .categoryName("Coffee")
                .categoryType(TransactionType.INCOME)
                .build();
        when(categoryRepository.findByIdAndUserId(categoryId, USER_ID)).thenReturn(Optional.of(existing));

        assertThrows(IllegalStateException.class,
                () -> service.updateCategory(categoryId, request, USER_ID));

        verify(categoryRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
