package com.finsight.finsight_ai.dto;


import com.finsight.finsight_ai.entity.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class CategoryResponse {

    private UUID id; //important for frontend operations.
    private String categoryName;
    private TransactionType transactionType;


    private String icon;
    private String color;
    private LocalDate createdAt;

}
