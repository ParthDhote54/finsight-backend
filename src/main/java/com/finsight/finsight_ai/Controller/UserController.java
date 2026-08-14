package com.finsight.finsight_ai.Controller;

import com.finsight.finsight_ai.Service.UserService;
import com.finsight.finsight_ai.dto.AuthResponse;
import com.finsight.finsight_ai.dto.LoginRequest;
import com.finsight.finsight_ai.dto.UserRegisterationRequest;
import com.finsight.finsight_ai.dto.UserResponse;
import com.finsight.finsight_ai.entity.User;
import com.finsight.finsight_ai.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<UserResponse> registerUser(@Valid @RequestBody UserRegisterationRequest request) {
        User registeredUser = userService.registerUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(registeredUser));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> loginUser(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = userService.login(request.getEmail(), request.getRawPassword());
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        UserResponse response = userService.getUserProfile(principal.getUserId());
        return ResponseEntity.ok(response);
    }
}
