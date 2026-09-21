package com.salestracker.stock;

import com.fasterxml.jackson.databind.JsonNode;
import com.salestracker.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StockIntegrationTest extends AbstractIntegrationTest {

    private Tenant tenant;
    private long platform;
    private long customer;

    @BeforeEach
    void setUp() throws Exception {
        tenant = registerTenant();
        platform = get(tenant, "/api/platforms", 200).get(0).get("id").asLong();
        customer = post(tenant, "/api/customers", "{\"name\":\"Buyer\"}", 201).get("id").asLong();
    }

    // ---- helpers ----

    private long product(String name, Integer stock, Integer threshold) throws Exception {
        return post(tenant, "/api/products", """
                {"name":"%s","costPrice":100,"sellingPrice":150,"stockQty":%s,"lowStockThreshold":%s}
                """.formatted(name, stock, threshold), 201).get("id").asLong();
    }

    private JsonNode order(String status, long productId, int qty) throws Exception {
        return post(tenant, "/api/orders", orderBody(status, productId, qty), 201);
    }

    private String orderBody(String status, long productId, int qty) {
        return """
                {"platformId":%d,"customerId":%d,"status":"%s",
                 "items":[{"productId":%d,"quantity":%d,"soldPrice":150}]}
                """.formatted(platform, customer, status, productId, qty);
    }

    private int stockOf(long productId) throws Exception {
        for (JsonNode p : get(tenant, "/api/products", 200).get("content")) {
            if (p.get("id").asLong() == productId) return p.get("stockQty").asInt();
        }
        throw new AssertionError("product not listed");
    }

    private JsonNode log(long productId) throws Exception {
        return get(tenant, "/api/stock/adjustments?productId=" + productId, 200).get("content");
    }

    private JsonNode adjust(long productId, int change, String reason, int expectedStatus) throws Exception {
        return post(tenant, "/api/stock/adjustments",
                "{\"productId\":%d,\"change\":%d,\"reason\":\"%s\",\"note\":\"n\"}".formatted(productId, change, reason),
                expectedStatus);
    }

    // ---- automatic decrement ----

    @Test
    void salesTakeStockAndReturnsGiveItBack() throws Exception {
        long rice = product("Rice", 10, null);
        JsonNode o = order("PAID", rice, 3);
        long orderId = o.get("id").asLong();
        assertEquals(7, stockOf(rice));

        JsonNode entry = log(rice).get(0);
        assertEquals("ORDER", entry.get("reason").asText());
        assertEquals(-3, entry.get("change").asInt());
        assertEquals(7, entry.get("stockAfter").asInt());
        assertEquals(orderId, entry.get("orderId").asLong());

        patch(tenant, "/api/orders/" + orderId + "/status", "{\"status\":\"RETURNED\"}", 200);
        assertEquals(10, stockOf(rice));
        // Setting the same status again, or moving between two holding statuses, must not double count.
        patch(tenant, "/api/orders/" + orderId + "/status", "{\"status\":\"RETURNED\"}", 200);
        assertEquals(10, stockOf(rice));
        patch(tenant, "/api/orders/" + orderId + "/status", "{\"status\":\"PENDING\"}", 200);
        assertEquals(7, stockOf(rice));
        patch(tenant, "/api/orders/" + orderId + "/status", "{\"status\":\"PAID\"}", 200);
        assertEquals(7, stockOf(rice));
        patch(tenant, "/api/orders/" + orderId + "/status", "{\"status\":\"CANCELLED\"}", 200);
        assertEquals(10, stockOf(rice));
    }

    @Test
    void editingAnOrderOnlyMovesTheDifference() throws Exception {
        long rice = product("Rice", 10, null);
        long oil = product("Oil", 5, null);
        long orderId = order("PAID", rice, 3).get("id").asLong();

        put(tenant, "/api/orders/" + orderId, orderBody("PAID", rice, 5), 200);
        assertEquals(5, stockOf(rice));
        put(tenant, "/api/orders/" + orderId, orderBody("PAID", rice, 2), 200);
        assertEquals(8, stockOf(rice));

        // Swapping the line to a different product returns the old one and takes the new one.
        put(tenant, "/api/orders/" + orderId, orderBody("PAID", oil, 4), 200);
        assertEquals(10, stockOf(rice));
        assertEquals(1, stockOf(oil));
    }

    @Test
    void deletingAnOrderRestoresStockAndKeepsTheHistory() throws Exception {
        long rice = product("Rice", 10, null);
        long orderId = order("PENDING", rice, 4).get("id").asLong();
        assertEquals(6, stockOf(rice));

        delete(tenant, "/api/orders/" + orderId, 204);
        assertEquals(10, stockOf(rice));
        JsonNode entries = log(rice);
        assertEquals(3, entries.size()); // opening stock, sale, give-back
        assertTrue(entries.get(0).get("orderId").isNull());
    }

    @Test
    void productsWithoutStockAreNotTracked() throws Exception {
        long free = product("Service", null, null);
        long orderId = order("PAID", free, 2).get("id").asLong();
        assertEquals(0, log(free).size());
        patch(tenant, "/api/orders/" + orderId + "/status", "{\"status\":\"CANCELLED\"}", 200);
        assertEquals(0, log(free).size());
        adjust(free, 5, "RESTOCK", 409);
    }

    @Test
    void sellingPastZeroIsAllowedAndShowsTheShortfall() throws Exception {
        long rice = product("Rice", 2, null);
        order("PAID", rice, 5);
        assertEquals(-3, stockOf(rice));
    }

    // ---- the log ----

    @Test
    void productFormChangesAreLogged() throws Exception {
        long rice = product("Rice", 10, null);
        assertEquals("INITIAL", log(rice).get(0).get("reason").asText());
        assertEquals(10, log(rice).get(0).get("change").asInt());

        put(tenant, "/api/products/" + rice,
                "{\"name\":\"Rice\",\"costPrice\":100,\"sellingPrice\":150,\"stockQty\":14}", 200);
        JsonNode top = log(rice).get(0);
        assertEquals("CORRECTION", top.get("reason").asText());
        assertEquals(4, top.get("change").asInt());
        assertEquals(14, top.get("stockAfter").asInt());

        // Saving with the same quantity, or turning tracking off, writes nothing.
        put(tenant, "/api/products/" + rice,
                "{\"name\":\"Rice 1kg\",\"costPrice\":100,\"sellingPrice\":150,\"stockQty\":14}", 200);
        put(tenant, "/api/products/" + rice, "{\"name\":\"Rice 1kg\",\"costPrice\":100,\"sellingPrice\":150}", 200);
        assertEquals(2, log(rice).size());
    }

    @Test
    void manualAdjustments() throws Exception {
        long rice = product("Rice", 10, null);

        JsonNode restock = adjust(rice, 20, "RESTOCK", 201);
        assertEquals(30, restock.get("stockAfter").asInt());
        assertEquals("Rice", restock.get("productName").asText());
        adjust(rice, -4, "DAMAGE", 201);
        adjust(rice, -1, "CORRECTION", 201);
        assertEquals(25, stockOf(rice));

        adjust(rice, 0, "CORRECTION", 400);
        adjust(rice, -5, "RESTOCK", 400);
        adjust(rice, 5, "DAMAGE", 400);
        adjust(rice, 5, "ORDER", 400);
        adjust(rice, -26, "CORRECTION", 400); // would go below zero
        adjust(rice, 1, "CORRECTION", 201);
        assertEquals(26, stockOf(rice));

        JsonNode damage = get(tenant, "/api/stock/adjustments?reason=DAMAGE", 200);
        assertEquals(1, damage.get("totalElements").asInt());
        assertEquals("n", damage.get("content").get(0).get("note").asText());
    }

    // ---- low-stock alert ----

    @Test
    void dashboardListsProductsAtOrBelowTheirThreshold() throws Exception {
        long low = product("Sugar", 3, 5);
        product("Salt", 50, 5);
        product("Tea", 2, null);   // no threshold, never alerts
        product("Rice", null, 5);  // untracked

        JsonNode alerts = get(tenant, "/api/dashboard", 200).get("lowStock");
        assertEquals(1, alerts.size());
        assertEquals(low, alerts.get(0).get("productId").asLong());
        assertEquals(3, alerts.get(0).get("stockQty").asInt());
        assertEquals(5, alerts.get(0).get("threshold").asInt());

        adjust(low, 10, "RESTOCK", 201);
        assertEquals(0, get(tenant, "/api/dashboard", 200).get("lowStock").size());
        order("PAID", low, 9);
        assertEquals(1, get(tenant, "/api/dashboard", 200).get("lowStock").size());
    }

    // ---- isolation ----

    @Test
    void otherBusinessesCannotSeeOrTouchTheLog() throws Exception {
        long rice = product("Rice", 10, null);
        Tenant other = registerTenant();
        assertEquals(0, get(other, "/api/stock/adjustments", 200).get("totalElements").asInt());
        assertEquals(0, get(other, "/api/stock/adjustments?productId=" + rice, 200).get("totalElements").asInt());
        post(other, "/api/stock/adjustments",
                "{\"productId\":%d,\"change\":5,\"reason\":\"RESTOCK\"}".formatted(rice), 404);
        assertEquals(10, stockOf(rice));
    }
}
