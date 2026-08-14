package com.finsight.finsight_ai.ai.chat.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Should correctly set and retrieve tenant ID on the current thread")
    void shouldSetAndGetTenantId() {
        UUID tenantId = UUID.randomUUID();
        TenantContext.set(tenantId);

        assertThat(TenantContext.get()).contains(tenantId);
        assertThat(TenantContext.require()).isEqualTo(tenantId);
    }

    @Test
    @DisplayName("Should throw NullPointerException when setting null tenant ID")
    void shouldRejectNullTenantId() {
        assertThatThrownBy(() -> TenantContext.set(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("userId bound to TenantContext must not be null");
    }

    @Test
    @DisplayName("Should throw IllegalStateException when require() is called without context")
    void shouldThrowWhenRequireCalledWithoutContext() {
        assertThatThrownBy(TenantContext::require)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No active TenantContext bound to current thread");
    }

    @Test
    @DisplayName("Should properly clear thread local value to prevent memory leaks")
    void shouldClearContext() {
        UUID tenantId = UUID.randomUUID();
        TenantContext.set(tenantId);
        TenantContext.clear();

        assertThat(TenantContext.get()).isEmpty();
        assertThatThrownBy(TenantContext::require)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Should isolate tenant contexts across concurrent threads")
    void shouldIsolateContextBetweenThreads() throws Exception {
        UUID thread1Tenant = UUID.randomUUID();
        UUID thread2Tenant = UUID.randomUUID();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<UUID> thread2ObservedTenant = new AtomicReference<>();

        TenantContext.set(thread1Tenant);

        Thread thread2 = new Thread(() -> {
            TenantContext.set(thread2Tenant);
            try {
                latch.await();
            } catch (InterruptedException ignored) {}
            thread2ObservedTenant.set(TenantContext.require());
            TenantContext.clear();
        });

        thread2.start();
        latch.countDown();
        thread2.join();

        assertThat(TenantContext.require()).isEqualTo(thread1Tenant);
        assertThat(thread2ObservedTenant.get()).isEqualTo(thread2Tenant);
    }
}
