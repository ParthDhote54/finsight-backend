package com.finsight.finsight_ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor //data carriers,not database entities.
public class AuthResponse {
    private String token;
}
