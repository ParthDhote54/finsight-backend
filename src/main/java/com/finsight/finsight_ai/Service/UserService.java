package com.finsight.finsight_ai.Service;

import com.finsight.finsight_ai.dto.AuthResponse;
import com.finsight.finsight_ai.dto.UserRegisterationRequest;
import com.finsight.finsight_ai.dto.UserResponse;
import com.finsight.finsight_ai.entity.User;
import com.finsight.finsight_ai.exception.InvalidCredentialsException;
import com.finsight.finsight_ai.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    @Autowired
    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final JwtService jwtService;
    @Transactional
    public User registerUser(UserRegisterationRequest request){

        if(userRepository.existsByEmail(request.getEmail())){
            throw new IllegalStateException("Email is already Registered");
        }
        User newUser = new User();
        newUser.setEmail(request.getEmail());
        newUser.setDisplayName(request.getDisplayName());
        String secureHash = passwordEncoder.encode(request.getRawPassword());
        newUser.setPasswordHash(secureHash);
        return userRepository.save(newUser);
    }

    public AuthResponse login(String email, String rawPassword) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if(!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }


        String token = jwtService.generateToken(user);
        return new AuthResponse(token);
    }

    public UserResponse getUserProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found"));
        return UserResponse.from(user);
    }
}
