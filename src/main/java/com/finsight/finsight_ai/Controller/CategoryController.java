package com.finsight.finsight_ai.Controller;

import com.finsight.finsight_ai.Service.CategoryService;
import com.finsight.finsight_ai.dto.CategoryRequest;
import com.finsight.finsight_ai.dto.CategoryResponse;
import com.finsight.finsight_ai.entity.TransactionType;
import com.finsight.finsight_ai.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {
    private final CategoryService categoryService;
    @PostMapping
    public ResponseEntity<CategoryResponse>createCategory(
            @Valid @RequestBody CategoryRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        CategoryResponse response = categoryService.createCategory(request, principal.getUserId());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);

    }

    @GetMapping()
    public ResponseEntity<Page<CategoryResponse>>getAllCategories(
            @AuthenticationPrincipal UserPrincipal principal,
            Pageable pagable,
            @RequestParam(required = false) TransactionType type) {
        Page<CategoryResponse> categories = categoryService.getAllCategories(principal.getUserId(), pagable, type);

        return ResponseEntity.status(HttpStatus.OK).body(categories);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable("id") UUID categoryId,
            @Valid @RequestBody CategoryRequest categoryRequest,
            @AuthenticationPrincipal UserPrincipal principal) {

        CategoryResponse categoryResponse = categoryService.updateCategory(categoryId, categoryRequest, principal.getUserId());

        return ResponseEntity.ok(categoryResponse);
    }
}
