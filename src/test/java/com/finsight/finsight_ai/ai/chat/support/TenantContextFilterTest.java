package com.finsight.finsight_ai.ai.chat.support;

import com.finsight.finsight_ai.security.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantContextFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private TenantContextFilter tenantContextFilter;

    @BeforeEach
    void setUp() {
        tenantContextFilter = new TenantContextFilter();
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    @DisplayName("Should set TenantContext for authenticated request with valid UserPrincipal")
    void shouldSetTenantContextForAuthenticatedUser() throws Exception {
        UUID expectedUserId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal();
        principal.setUserId(expectedUserId);

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);

        AtomicBoolean filterExecuted = new AtomicBoolean(false);
        doAnswer(invocation -> {
            filterExecuted.set(true);
            assertThat(TenantContext.require()).isEqualTo(expectedUserId);
            return null;
        }).when(filterChain).doFilter(request, response);

        tenantContextFilter.doFilter(request, response, filterChain);

        assertThat(filterExecuted.get()).isTrue();
        assertThat(TenantContext.get()).isEmpty(); // Verify cleared post-filter
    }

    @Test
    @DisplayName("Should NOT set TenantContext for unauthenticated or anonymous requests")
    void shouldNotSetTenantContextForAnonymousUser() throws Exception {
        SecurityContextHolder.clearContext();

        doAnswer(invocation -> {
            assertThat(TenantContext.get()).isEmpty();
            return null;
        }).when(filterChain).doFilter(request, response);

        tenantContextFilter.doFilter(request, response, filterChain);

        assertThat(TenantContext.get()).isEmpty();
    }

    @Test
    @DisplayName("Should ALWAYS clear TenantContext in finally block even if downstream throws an exception")
    void shouldClearTenantContextOnDownstreamException() throws Exception {
        UUID expectedUserId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal();
        principal.setUserId(expectedUserId);

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);

        doThrow(new ServletException("Downstream error")).when(filterChain).doFilter(request, response);

        assertThatThrownBy(() -> tenantContextFilter.doFilter(request, response, filterChain))
                .isInstanceOf(ServletException.class)
                .hasMessage("Downstream error");

        assertThat(TenantContext.get()).isEmpty(); // Guarantees cleanup on exception
    }
}
