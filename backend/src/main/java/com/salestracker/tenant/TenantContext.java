package com.salestracker.tenant;

/**
 * The tenant of the request currently running on this thread - set once by JwtAuthFilter right after it
 * resolves the JWT, read by TenantScopedRepositoryImpl, cleared in JwtAuthFilter's finally block.
 * A plain ThreadLocal is enough: this app has no reactive pipeline or thread handoff mid-request, so one
 * request always runs start-to-finish on one thread, and clearing it after every request stops a value
 * leaking into the next one on a pooled thread.
 */
public final class TenantContext {
    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(Long tenantId) { CURRENT.set(tenantId); }

    public static Long get() { return CURRENT.get(); }

    public static void clear() { CURRENT.remove(); }
}
