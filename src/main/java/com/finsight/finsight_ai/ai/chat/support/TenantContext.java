package com.finsight.finsight_ai.ai.chat.support;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * ThreadLocal container providing secure, thread-isolated tenant context access.
 *
 * <p>Critical Security Context:
 * <ul>
 *   <li>Used exclusively by AI tools to resolve the active tenant ID without relying on LLM arguments.</li>
 *   <li>MUST be cleared in a finally block post-request to prevent thread pool contamination and memory leaks.</li>
 * </ul>
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> TENANT_HOLDER = new ThreadLocal<>();

    private TenantContext() {
        // Prevent instantiation of utility class.
    }

    /**
     * Binds the authenticated tenant ID to the current thread.
     *
     * @param userId The non-null authenticated tenant ID.
     * @throws NullPointerException if userId is null.
     */
    public static void set(UUID userId) {
        Objects.requireNonNull(userId, "userId bound to TenantContext must not be null");
        TENANT_HOLDER.set(userId);
    }

    /**
     * Retrieves the optional tenant ID associated with the current thread.
     *
     * @return Optional containing the active tenant ID, or empty if unbound.
     */
    public static Optional<UUID> get() {
        return Optional.ofNullable(TENANT_HOLDER.get());
    }

    /**
     * Retrieves the required tenant ID associated with the current thread.
     * Throws an exception if no tenant context exists.
     *
     * @return the active Tenant ID.
     * @throws IllegalStateException if called on a thread with no bound tenant context.
     */
    public static UUID require() {
        return get().orElseThrow(() ->
                new IllegalStateException("No active TenantContext bound to current thread. Execution halted for multi-tenant safety."));
    }

    /**
     * Purges the tenant ID from the current thread to prevent memory leaks and cross-tenant pollution in pooled thread environments.
     */
    public static void clear() {
        TENANT_HOLDER.remove();
    }
}
