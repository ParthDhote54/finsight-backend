package com.finsight.finsight_ai.Mapper;

import com.finsight.finsight_ai.dto.CategoryRequest;
import com.finsight.finsight_ai.dto.CategoryResponse;
import com.finsight.finsight_ai.entity.Category;

import java.time.LocalDate;

public class CategoryMapper {

    public static Category requestDtoToCategory(CategoryRequest request){


        return Category.builder()
                .name(request.getCategoryName())
                .type(request.getCategoryType())
                .icon(request.getIcon())
                .color(request.getColor())
                .build();
    }

    public static CategoryResponse categoryToResponseDto(Category category){

        return CategoryResponse.builder()
                .id(category.getId())
                .categoryName(category.getName())
                .transactionType(category.getType())
                .icon(category.getIcon())
                .color(category.getColor())
                .createdAt(LocalDate.now())
                .build();
    }
}
