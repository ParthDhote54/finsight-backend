package com.finsight.finsight_ai.Service;


import com.finsight.finsight_ai.Mapper.CategoryMapper;
import com.finsight.finsight_ai.dto.CategoryRequest;
import com.finsight.finsight_ai.dto.CategoryResponse;
import com.finsight.finsight_ai.entity.Category;

import com.finsight.finsight_ai.entity.TransactionType;
import com.finsight.finsight_ai.entity.User;
import com.finsight.finsight_ai.exception.ResourceNotFoundException;
import com.finsight.finsight_ai.repository.CategoryRepository;
import com.finsight.finsight_ai.repository.CategorySpecifications;
import com.finsight.finsight_ai.repository.UserRepository;

import lombok.AllArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@AllArgsConstructor
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request, UUID userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        boolean exists = categoryRepository.existsByNameIgnoreCaseAndTypeAndUserId(
                request.getCategoryName(), request.getCategoryType(), userId);
        if(exists) {
            throw new IllegalStateException("category already exists");
        }

        Category category = CategoryMapper.requestDtoToCategory(request);
        category.setUser(user);
        categoryRepository.save(category);

        return CategoryMapper.categoryToResponseDto(category);
    }

    @Transactional(readOnly = true)
    public Page<CategoryResponse> getAllCategories(UUID userId, Pageable pageable, TransactionType type) {

        Specification<Category> spec = CategorySpecifications.getCategoriesForUser(userId, type);

        Page<Category> categoryPage = categoryRepository.findAll(spec, pageable);

        // Map to DTO seamlessly
        return categoryPage.map(CategoryMapper::categoryToResponseDto);
    }

    @Transactional
    public CategoryResponse updateCategory(UUID categoryId, CategoryRequest categoryRequest, UUID userId) {

        Category category = categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        if (category.getType() != categoryRequest.getCategoryType()) {
            throw new IllegalStateException("Category type cannot be changed");
        }

        // 3. Mutate the Managed Entity directly (Hibernate is watching this object!)
        if (categoryRequest.getCategoryName() != null) {
            category.setName(categoryRequest.getCategoryName());
        }

        if (categoryRequest.getCategoryType() != null) {
            category.setType(categoryRequest.getCategoryType());
        }

        if (categoryRequest.getIcon() != null) {
            category.setIcon(categoryRequest.getIcon());
        }

        if (categoryRequest.getColor() != null) {
            category.setColor(categoryRequest.getColor());
        }

        // 4. (Optional but recommended) Ensure the new name/type combination doesn't already exist
        // Note: You only need this check if they actually changed the name or type!
        boolean exists = categoryRepository.existsByNameIgnoreCaseAndTypeAndUserIdAndIdNot(
                category.getName(),
                category.getType(),
                userId,
                categoryId
        );

        if (exists) {
            throw new IllegalStateException("Another category already uses this name and type.");
        }

        // 5. Save the mutated Managed Entity
        categoryRepository.save(category);

        return CategoryMapper.categoryToResponseDto(category);
    }
}
