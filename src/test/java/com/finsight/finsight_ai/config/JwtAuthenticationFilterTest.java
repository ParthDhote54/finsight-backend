package com.finsight.finsight_ai.config;

import com.finsight.finsight_ai.Service.JwtService;
import com.finsight.finsight_ai.entity.User;
import com.finsight.finsight_ai.security.UserPrincipal;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JwtAuthenticationFilterTest {

    private static final String TEST_SECRET = "test-only-secret-that-is-at-least-32-bytes-long";
    private final JwtService jwtService = new JwtService(TEST_SECRET, 60_000);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void missingHeaderContinuesWithoutAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }

    @Test
    void validTokenCreatesExpectedPrincipalWithoutLoggingCredentials() throws Exception {
        User user = user();
        String token = jwtService.generateToken(user);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        PrintStream originalOut = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();

        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            filter.doFilterInternal(request, response, chain);
        } finally {
            System.setOut(originalOut);
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = assertInstanceOf(UserPrincipal.class, authentication.getPrincipal());
        assertEquals(user.getId(), principal.getUserId());
        assertEquals(user.getEmail(), principal.getEmail());
        String output = captured.toString(StandardCharsets.UTF_8);
        assertTrue(output.isEmpty());
        verify(chain).doFilter(request, response);
    }

    @Test
    void tamperedTokenContinuesWithoutAuthentication() throws Exception {
        String token = jwtService.generateToken(user());
        String tampered = token.substring(0, token.lastIndexOf('.') + 1) + "INVALID_SIGNATURE";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + tampered);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }

    @Test
    void existingAuthenticationIsNotOverwritten() throws Exception {
        Authentication existing = new TestingAuthenticationToken("existing", null);
        SecurityContextHolder.getContext().setAuthentication(existing);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwtService.generateToken(user()));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertSame(existing, SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }

    private User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setDisplayName("Test User");
        return user;
    }
}
