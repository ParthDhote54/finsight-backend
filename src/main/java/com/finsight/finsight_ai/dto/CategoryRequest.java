package com.finsight.finsight_ai.dto;

import com.finsight.finsight_ai.entity.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryRequest {

    @NotBlank(message = "category cannot be empty")
    @Size(max = 255, message = "category cannot exceed 255 characters")
    private String categoryName;

    @NotNull(message = "category type is required")
    private TransactionType categoryType;

    @Size(max = 255, message = "icon cannot exceed 255 characters")
    private String icon;
    @Size(max = 255, message = "color cannot exceed 255 characters")
    private String color;
}
