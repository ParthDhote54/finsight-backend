package com.finsight.finsight_ai.Controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.finsight_ai.Service.UserService;
import com.finsight.finsight_ai.dto.UserRegisterationRequest;
import com.finsight.finsight_ai.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private UserService userService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService)).build();
    }

    @Test
    void registrationResponseNeverContainsPasswordMaterial() throws Exception {
        User saved = new User();
        saved.setId(UUID.randomUUID());
        saved.setEmail("user@example.com");
        saved.setDisplayName("Test User");
        saved.setPasswordHash("bcrypt-hash-must-never-leave-the-api");
        saved.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        when(userService.registerUser(any(UserRegisterationRequest.class))).thenReturn(saved);

        UserRegisterationRequest request = new UserRegisterationRequest();
        request.setEmail("user@example.com");
        request.setDisplayName("Test User");
        request.setRawPassword("raw-password");

        mockMvc.perform(post("/api/v1/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(saved.getId().toString()))
                .andExpect(jsonPath("$.email").value(saved.getEmail()))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.rawPassword").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }
}
