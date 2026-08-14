package com.finsight.finsight_ai.ai.chat.support;

import com.finsight.finsight_ai.security.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter responsible for bridging Spring Security's SecurityContext into the ThreadLocal TenantContext
 * for down-stream financial AI tools.
 *
 * <p>Guarantees that:
 * <ul>
 *   <li>Only authenticated requests with a valid UserPrincipal set the tenant ID.</li>
 *   <li>Anonymous or unauthenticated requests leave TenantContext empty.</li>
 *   <li>TenantContext is ALWAYS cleared in a finally block regardless of exceptions thrown downstream.</li>
 * </ul>
 */
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()
                    && authentication.getPrincipal() instanceof UserPrincipal principal
                    && principal.getUserId() != null) {
                UUID userId = principal.getUserId();
                TenantContext.set(userId);
            }

            filterChain.doFilter(request, response);

        } finally {
            // Critical security guardrail: guarantee thread local cleanup after every request
            TenantContext.clear();
        }
    }
}
