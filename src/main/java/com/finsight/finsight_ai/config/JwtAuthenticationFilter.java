package com.finsight.finsight_ai.config;

import com.finsight.finsight_ai.Service.JwtService;
import com.finsight.finsight_ai.security.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            final String jwt = authHeader.substring(7);
            try {
                if (jwtService.isTokenValid(jwt)) {
                    String email = jwtService.extractEmail(jwt);
                    UUID userId = jwtService.extractUserId(jwt);
                    if (email != null && !email.isBlank()) {
                        UserPrincipal principal = new UserPrincipal();
                        principal.setEmail(email);
                        principal.setUserId(userId);
                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                principal, null, new ArrayList<>()
                        );
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    }
                }
            } catch (RuntimeException ignored) {
                // A signed token with malformed claims is unauthenticated, never a server error.
            }
        }

        filterChain.doFilter(request, response);
    }
}
