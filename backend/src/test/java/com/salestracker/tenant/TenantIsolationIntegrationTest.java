package com.salestracker.tenant;

import com.fasterxml.jackson.databind.JsonNode;
import com.salestracker.AbstractIntegrationTest;
import com.salestracker.product.Product;
import com.salestracker.product.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 7a: explicit, dedicated tenant-isolation coverage for the resources that didn't already have their
 * own spot-check (expense, stock and report each got one alongside their own feature work - see their
 * integration tests). Two businesses, A and B; A creates one of everything, B is tried against all of it
 * through every verb the API exposes, and B's own reads/writes/lists must never surface or touch A's rows.
 */
class TenantIsolationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Test
    void productsAreIsolated() throws Exception {
        Tenant a = registerTenant();
        long productId = post(a, "/api/products",
                "{\"name\":\"Rice\",\"costPrice\":100,\"sellingPrice\":150}", 201).get("id").asLong();

        Tenant b = registerTenant();
        assertEquals(0, get(b, "/api/products", 200).get("content").size());
        put(b, "/api/products/" + productId, "{\"name\":\"Hijacked\",\"costPrice\":1,\"sellingPrice\":2}", 404);
        delete(b, "/api/products/" + productId, 404);

        // A's product must still be untouched and visible only to A.
        JsonNode listedByA = get(a, "/api/products", 200).get("content");
        assertEquals(1, listedByA.size());
        assertEquals("Rice", listedByA.get(0).get("name").asText());
    }

    @Test
    void customersAreIsolated() throws Exception {
        Tenant a = registerTenant();
        long customerId = post(a, "/api/customers", "{\"name\":\"Karim\"}", 201).get("id").asLong();

        Tenant b = registerTenant();
        assertEquals(0, get(b, "/api/customers", 200).get("content").size());
        put(b, "/api/customers/" + customerId, "{\"name\":\"Hijacked\"}", 404);
        delete(b, "/api/customers/" + customerId, 404);

        // B cannot even reference A's customer when quick-recording a sale.
        long bPlatform = get(b, "/api/platforms", 200).get(0).get("id").asLong();
        long bProduct = post(b, "/api/products", "{\"name\":\"Oil\",\"costPrice\":1,\"sellingPrice\":2}", 201)
                .get("id").asLong();
        post(b, "/api/orders", """
                {"platformId":%d,"customerId":%d,"status":"PAID","items":[{"productId":%d,"quantity":1,"soldPrice":2}]}
                """.formatted(bPlatform, customerId, bProduct), 400);
    }

    @Test
    void platformsAreIsolated() throws Exception {
        Tenant a = registerTenant();
        long platformId = post(a, "/api/platforms", "{\"name\":\"TikTok Shop\",\"commissionPct\":5}", 201)
                .get("id").asLong();

        Tenant b = registerTenant();
        // B's own (auto-seeded) platform list never includes A's, custom or default - each tenant got its own
        // Facebook Page / Daraz rows at registration, so the ids don't even collide.
        for (JsonNode p : get(b, "/api/platforms", 200)) {
            assertNotEquals(platformId, p.get("id").asLong());
        }
        put(b, "/api/platforms/" + platformId, "{\"name\":\"Hijacked\",\"commissionPct\":0}", 404);
        delete(b, "/api/platforms/" + platformId, 404);

        // B cannot record a sale against A's platform either.
        long bProduct = post(b, "/api/products", "{\"name\":\"Oil\",\"costPrice\":1,\"sellingPrice\":2}", 201)
                .get("id").asLong();
        post(b, "/api/orders", """
                {"platformId":%d,"newCustomer":{"name":"X"},"status":"PAID",
                 "items":[{"productId":%d,"quantity":1,"soldPrice":2}]}
                """.formatted(platformId, bProduct), 400);
    }

    @Test
    void ordersAreIsolatedIncludingDashboardAndReportAggregates() throws Exception {
        Tenant a = registerTenant();
        long aPlatform = get(a, "/api/platforms", 200).get(0).get("id").asLong();
        long aProduct = post(a, "/api/products", "{\"name\":\"Rice\",\"costPrice\":100,\"sellingPrice\":150}", 201)
                .get("id").asLong();
        JsonNode order = post(a, "/api/orders", """
                {"platformId":%d,"newCustomer":{"name":"Karim"},"status":"PAID",
                 "items":[{"productId":%d,"quantity":2,"soldPrice":150}]}
                """.formatted(aPlatform, aProduct), 201);
        long orderId = order.get("id").asLong();

        Tenant b = registerTenant();
        get(b, "/api/orders/" + orderId, 404);
        patch(b, "/api/orders/" + orderId + "/status", "{\"status\":\"CANCELLED\"}", 404);
        delete(b, "/api/orders/" + orderId, 404);
        assertEquals(0, get(b, "/api/orders", 200).get("content").size());

        // Aggregates: B's totals stay at zero despite A having real, paid revenue.
        JsonNode bDashboard = get(b, "/api/dashboard", 200);
        assertMoney("0", bDashboard.get("realized").get("revenue"));
        assertEquals(0, bDashboard.get("recentOrders").size());

        JsonNode bSummary = get(b, "/api/reports/summary", 200);
        assertMoney("0", bSummary.get("realized").get("revenue"));
        assertEquals(0, bSummary.get("byPlatform").size());

        // A's own view is untouched throughout.
        assertMoney("300.00", get(a, "/api/dashboard", 200).get("realized").get("revenue"));
    }

    private void assertMoney(String expected, JsonNode actual) {
        assertEquals(0, new java.math.BigDecimal(expected).compareTo(new java.math.BigDecimal(actual.asText())),
                () -> "expected " + expected + " but was " + actual.asText());
    }

    /**
     * Proves the Phase 7a systemic guard itself, not just the application code on top of it: call the plain,
     * un-scoped JpaRepository method directly (bypassing every one of this app's own tenant-scoped finder
     * methods entirely, standing in for a future query nobody thought to scope) and confirm
     * TenantScopedRepositoryImpl still refuses to hand back another tenant's row.
     */
    @Test
    void theBaseRepositoryItselfBlocksAnUnscopedLookup() throws Exception {
        Tenant a = registerTenant();
        long productId = post(a, "/api/products", "{\"name\":\"Rice\",\"costPrice\":100,\"sellingPrice\":150}", 201)
                .get("id").asLong();

        Tenant b = registerTenant();
        TenantContext.set(b.id());
        try {
            Optional<Product> viaPlainFindById = productRepository.findById(productId);
            assertTrue(viaPlainFindById.isEmpty(), "the bare findById must not leak another tenant's row");
            assertFalse(productRepository.existsById(productId));
            assertTrue(productRepository.findAllById(java.util.List.of(productId)).isEmpty());
        } finally {
            TenantContext.clear();
        }

        // Sanity check: the same lookup succeeds under the owning tenant, so this is real filtering, not a
        // generally-broken repository.
        TenantContext.set(a.id());
        try {
            assertTrue(productRepository.findById(productId).isPresent());
        } finally {
            TenantContext.clear();
        }
    }
}
