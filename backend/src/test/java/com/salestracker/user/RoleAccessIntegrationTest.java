package com.salestracker.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.salestracker.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 7b: Owner vs. Staff. Staff gets full day-to-day access (products, customers, platforms, orders,
 * stock) but no financial visibility (dashboard, reports, expenses, cost/profit figures, commission rate,
 * other users), per the permission model settled on for this phase.
 */
class RoleAccessIntegrationTest extends AbstractIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    private Tenant owner;

    @BeforeEach
    void setUp() throws Exception {
        owner = registerTenant();
    }

    private record StaffAccount(long id, String email, Tenant session) {}

    /** Creates a Staff account (a fresh email every call, like registerTenant()) and logs in as them. */
    private StaffAccount staffAccount() throws Exception {
        String email = "staff%d-%d@example.com".formatted(SEQ.incrementAndGet(), System.nanoTime());
        long id = post(owner, "/api/users",
                "{\"fullName\":\"Staffer\",\"email\":\"%s\",\"password\":\"password123\"}".formatted(email), 201)
                .get("id").asLong();
        JsonNode login = postAnonymous("/api/auth/login",
                "{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email), 200);
        Tenant session = new Tenant(login.at("/user/tenantId").asLong(), "Bearer " + login.get("token").asText());
        return new StaffAccount(id, email, session);
    }

    private Tenant staff() throws Exception {
        return staffAccount().session();
    }

    // ---- staff account management (owner only) ----

    @Test
    void onlyAnOwnerCanManageStaff() throws Exception {
        JsonNode created = post(owner, "/api/users",
                "{\"fullName\":\"Rina\",\"email\":\"rina@example.com\",\"password\":\"password123\"}", 201);
        assertEquals("STAFF", created.get("role").asText());
        assertTrue(created.get("active").asBoolean());

        Tenant rina = staff();
        get(rina, "/api/users", 403);
        post(rina, "/api/users", "{\"fullName\":\"X\",\"email\":\"x@example.com\",\"password\":\"password123\"}", 403);

        // The team list is the owner plus the two staff created above (the direct POST and staff()'s own).
        assertEquals(3, get(owner, "/api/users", 200).size());
    }

    @Test
    void theOwnerAccountCannotBeDeactivated() throws Exception {
        JsonNode me = get(owner, "/api/auth/me", 200);
        delete(owner, "/api/users/" + me.get("id").asLong(), 409);
    }

    @Test
    void deactivatingStaffRevokesAccessImmediatelyEvenWithAnExistingToken() throws Exception {
        StaffAccount rina = staffAccount();
        get(rina.session(), "/api/products", 200); // the token works before deactivation

        delete(owner, "/api/users/" + rina.id(), 204);

        // Same, already-issued token - must stop working right away, not just at next login.
        get(rina.session(), "/api/products", 401);
        postAnonymous("/api/auth/login", "{\"email\":\"%s\",\"password\":\"password123\"}".formatted(rina.email()), 401);
    }

    // ---- financial data is owner-only ----

    @Test
    void staffCannotReachDashboardReportsOrExpenses() throws Exception {
        Tenant staff = staff();
        get(staff, "/api/dashboard", 403);
        get(staff, "/api/reports/summary", 403);
        get(staff, "/api/reports/trend", 403);
        get(staff, "/api/reports/products", 403);
        get(staff, "/api/expenses", 403);
        post(staff, "/api/expenses", "{\"type\":\"MISC\",\"amount\":10,\"expenseDate\":\"2026-01-01\"}", 403);
    }

    @Test
    void staffDoesNotSeeCostOrProfitOnProductsOrOrders() throws Exception {
        Tenant staff = staff();
        long platform = get(staff, "/api/platforms", 200).get(0).get("id").asLong();

        long productId = post(staff, "/api/products",
                "{\"name\":\"Rice\",\"costPrice\":100,\"sellingPrice\":150}", 201).get("id").asLong();
        // Staff's own create response already hides it...
        assertTrue(post(staff, "/api/products", "{\"name\":\"Oil\",\"costPrice\":50,\"sellingPrice\":80}", 201)
                .get("costPrice").isNull());
        // ...and so does the list, even though the product really does have a cost price underneath (the
        // owner's view of the very same product proves that).
        JsonNode staffList = get(staff, "/api/products", 200).get("content");
        for (JsonNode p : staffList) assertTrue(p.get("costPrice").isNull());
        JsonNode ownerList = get(owner, "/api/products", 200).get("content");
        assertTrue(ownerList.get(0).get("costPrice").asDouble() > 0);

        JsonNode order = post(staff, "/api/orders", """
                {"platformId":%d,"newCustomer":{"name":"Karim"},"status":"PAID",
                 "items":[{"productId":%d,"quantity":2,"soldPrice":150}]}
                """.formatted(platform, productId), 201);
        assertTrue(order.get("cost").isNull());
        assertTrue(order.get("profit").isNull());
        assertTrue(order.get("commissionPct").isNull());
        assertEquals(0, order.get("expenses").size());
        JsonNode item = order.get("items").get(0);
        assertTrue(item.get("costPrice").isNull());
        assertTrue(item.get("lineCost").isNull());
        assertTrue(item.get("lineProfit").isNull());
        assertFalse(item.get("lineRevenue").isNull(), "revenue itself is not hidden, only cost/profit");

        long orderId = order.get("id").asLong();
        JsonNode fetched = get(staff, "/api/orders/" + orderId, 200);
        assertTrue(fetched.get("profit").isNull());
        JsonNode listed = get(staff, "/api/orders", 200).get("content").get(0);
        assertTrue(listed.get("profit").isNull());
        assertFalse(listed.get("revenue").isNull());

        // The owner's view of the exact same order has the real figures.
        JsonNode ownerView = get(owner, "/api/orders/" + orderId, 200);
        assertEquals(0, new java.math.BigDecimal("300.00").compareTo(new java.math.BigDecimal(ownerView.get("revenue").asText())));
        assertFalse(ownerView.get("profit").isNull());
    }

    @Test
    void staffCanEditAProductWithoutRetypingACostPriceTheyCannotSee() throws Exception {
        Tenant staff = staff();
        long ownerCreated = post(owner, "/api/products", "{\"name\":\"Rice\",\"costPrice\":100,\"sellingPrice\":150}", 201)
                .get("id").asLong();

        // Staff renames it without a costPrice field at all - the real cost underneath must survive untouched.
        put(staff, "/api/products/" + ownerCreated, "{\"name\":\"Rice 1kg\",\"sellingPrice\":160}", 200);
        JsonNode ownerView = get(owner, "/api/products", 200).get("content");
        JsonNode row = null;
        for (JsonNode p : ownerView) if (p.get("id").asLong() == ownerCreated) row = p;
        assertEquals("Rice 1kg", row.get("name").asText());
        assertEquals(0, new java.math.BigDecimal("100.00").compareTo(new java.math.BigDecimal(row.get("costPrice").asText())));

        // A brand new product Staff creates without a costPrice starts at 0, not rejected outright.
        JsonNode created = post(staff, "/api/products", "{\"name\":\"Oil\",\"sellingPrice\":80}", 201);
        assertTrue(created.get("costPrice").isNull()); // hidden either way, but...
        JsonNode ownerViewOfNew = get(owner, "/api/products", 200).get("content");
        JsonNode oilRow = null;
        for (JsonNode p : ownerViewOfNew) if (p.get("id").asLong() == created.get("id").asLong()) oilRow = p;
        assertEquals(0, java.math.BigDecimal.ZERO.compareTo(new java.math.BigDecimal(oilRow.get("costPrice").asText())));
    }

    // ---- staff still fully operates day-to-day sales ----

    @Test
    void staffHasFullAccessToProductsCustomersPlatformsOrdersAndStock() throws Exception {
        Tenant staff = staff();
        long platform = get(staff, "/api/platforms", 200).get(0).get("id").asLong();

        long productId = post(staff, "/api/products", "{\"name\":\"Rice\",\"costPrice\":100,\"sellingPrice\":150,\"stockQty\":10}", 201)
                .get("id").asLong();
        put(staff, "/api/products/" + productId, "{\"name\":\"Rice 1kg\",\"costPrice\":100,\"sellingPrice\":150,\"stockQty\":10}", 200);

        long customerId = post(staff, "/api/customers", "{\"name\":\"Karim\"}", 201).get("id").asLong();
        put(staff, "/api/customers/" + customerId, "{\"name\":\"Karim Uddin\"}", 200);

        long newPlatform = post(staff, "/api/platforms", "{\"name\":\"TikTok Shop\"}", 201).get("id").asLong();
        put(staff, "/api/platforms/" + newPlatform, "{\"name\":\"TikTok Shop BD\",\"commissionPct\":0}", 200);

        long orderId = post(staff, "/api/orders", """
                {"platformId":%d,"customerId":%d,"status":"PAID","items":[{"productId":%d,"quantity":1,"soldPrice":150}]}
                """.formatted(platform, customerId, productId), 201).get("id").asLong();
        patch(staff, "/api/orders/" + orderId + "/status", "{\"status\":\"RETURNED\"}", 200);

        post(staff, "/api/stock/adjustments",
                "{\"productId\":%d,\"change\":5,\"reason\":\"RESTOCK\",\"note\":\"n\"}".formatted(productId), 201);
        get(staff, "/api/stock/adjustments", 200);
    }

    @Test
    void staffCanCreatePlatformsButNotSetOrChangeTheCommissionRate() throws Exception {
        Tenant staff = staff();

        // Creating one: any commissionPct submitted is ignored, not rejected - it's just forced to 0.
        JsonNode created = post(staff, "/api/platforms", "{\"name\":\"TikTok Shop\",\"commissionPct\":9}", 201);
        assertEquals(0, new java.math.BigDecimal(created.get("commissionPct").asText()).signum());
        long id = created.get("id").asLong();

        // Renaming it (unchanged rate) is fine.
        put(staff, "/api/platforms/" + id, "{\"name\":\"TikTok Shop BD\",\"commissionPct\":0}", 200);
        // Actually trying to change the rate is not.
        put(staff, "/api/platforms/" + id, "{\"name\":\"TikTok Shop BD\",\"commissionPct\":5}", 403);

        // The owner can.
        JsonNode updated = put(owner, "/api/platforms/" + id, "{\"name\":\"TikTok Shop BD\",\"commissionPct\":5}", 200);
        assertEquals(0, new java.math.BigDecimal("5.00").compareTo(new java.math.BigDecimal(updated.get("commissionPct").asText())));
    }
}
