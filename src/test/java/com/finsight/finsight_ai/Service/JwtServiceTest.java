package com.finsight.finsight_ai.Service;

import com.finsight.finsight_ai.entity.User;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String TEST_SECRET = "test-only-secret-that-is-at-least-32-bytes-long";

    @Test
    void generatesAndValidatesConfiguredToken() {
        JwtService jwtService = new JwtService(TEST_SECRET, 60_000);
        User user = user();

        String token = jwtService.generateToken(user);

        assertTrue(jwtService.isTokenValid(token));
        assertEquals(user.getId(), jwtService.extractUserId(token));
        assertEquals(user.getEmail(), jwtService.extractEmail(token));
    }

    @Test
    void rejectsTamperedToken() {
        JwtService jwtService = new JwtService(TEST_SECRET, 60_000);
        String token = jwtService.generateToken(user());
        String tampered = token.substring(0, token.length() - 10)
                + (token.charAt(token.length() - 10) == 'A' ? 'B' : 'A')
                + token.substring(token.length() - 9);

        assertFalse(jwtService.isTokenValid(tampered));
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        JwtService issuer = new JwtService(TEST_SECRET, 60_000);
        JwtService verifier = new JwtService("another-test-only-secret-that-is-at-least-32-bytes", 60_000);

        assertFalse(verifier.isTokenValid(issuer.generateToken(user())));
    }

    @Test
    void respectsConfiguredExpiration() throws InterruptedException {
        JwtService jwtService = new JwtService(TEST_SECRET, 5);
        String token = jwtService.generateToken(user());

        Thread.sleep(25);

        assertFalse(jwtService.isTokenValid(token));
    }

    @Test
    void rejectsMissingOrWeakSecret() {
        assertThrows(IllegalStateException.class, () -> new JwtService(" ", 60_000));
        assertThrows(IllegalStateException.class, () -> new JwtService("too-short", 60_000));
    }

    @Test
    void rejectsNonPositiveExpiration() {
        assertThrows(IllegalStateException.class, () -> new JwtService(TEST_SECRET, 0));
    }

    private User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setDisplayName("Test User");
        return user;
    }
}
