package com.salestracker.customer;

import com.fasterxml.jackson.databind.JsonNode;
import com.salestracker.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class CustomerInsightsIntegrationTest extends AbstractIntegrationTest {

    private Tenant tenant;
    private long facebook;
    private long rice; // cost 100

    @BeforeEach
    void setUp() throws Exception {
        tenant = registerTenant();
        facebook = get(tenant, "/api/platforms", 200).get(0).get("id").asLong();
        rice = post(tenant, "/api/products", "{\"name\":\"Rice\",\"costPrice\":100,\"sellingPrice\":150}", 201).get("id").asLong();
    }

    private long customer(String name) throws Exception {
        return post(tenant, "/api/customers", "{\"name\":\"%s\"}".formatted(name), 201).get("id").asLong();
    }

    /** Two units at 150 plus a 60 delivery charge: the customer pays 360. */
    private void order(long customerId, String status, String orderedAt) throws Exception {
        post(tenant, "/api/orders", """
                {"platformId":%d,"customerId":%d,"status":"%s","deliveryCharge":60,"orderedAt":"%s",
                 "items":[{"productId":%d,"quantity":2,"soldPrice":150}]}
                """.formatted(facebook, customerId, status, orderedAt, rice), 201);
    }

    private JsonNode insights(String name) throws Exception {
        for (JsonNode c : get(tenant, "/api/customers?size=100", 200).get("content")) {
            if (c.get("name").asText().equals(name)) return c.get("insights");
        }
        throw new AssertionError("No customer " + name);
    }

    private static void assertMoney(String expected, JsonNode actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual.decimalValue()), "expected " + expected + " but was " + actual);
    }

    @Test
    void eachCustomerShowsTheirSpendOrdersAndReturnRate() throws Exception {
        long good = customer("Good");
        order(good, "PAID", "2026-01-05T10:00:00");
        order(good, "PAID", "2026-02-05T10:00:00");
        order(good, "PENDING", "2026-03-05T10:00:00");

        JsonNode g = insights("Good");
        assertEquals(3, g.get("orders").asLong());
        assertEquals(2, g.get("paid").asLong());
        assertEquals(1, g.get("pending").asLong());
        assertMoney("720", g.get("spent")); // two paid orders of 360; the pending one isn't money yet
        assertEquals(0, g.get("returnRatePct").asInt());
        assertFalse(g.get("flagged").asBoolean());
        assertEquals("2026-03-05T10:00:00", g.get("lastOrderAt").asText());

        customer("New");
        JsonNode n = insights("New");
        assertEquals(0, n.get("orders").asLong());
        assertMoney("0", n.get("spent"));
        assertTrue(n.get("returnRatePct").isNull());
        assertTrue(n.get("lastOrderAt").isNull());
    }

    @Test
    void customersWhoOftenReturnOrCancelAreFlagged() throws Exception {
        long risky = customer("Risky");
        order(risky, "PAID", "2026-01-01T10:00:00");
        order(risky, "RETURNED", "2026-01-02T10:00:00");
        assertFalse(insights("Risky").get("flagged").asBoolean()); // one return is not a pattern

        order(risky, "CANCELLED", "2026-01-03T10:00:00");
        order(risky, "PENDING", "2026-01-04T10:00:00"); // still open: not counted in the rate
        JsonNode r = insights("Risky");
        assertEquals(67, r.get("returnRatePct").asInt()); // 2 of 3 settled
        assertTrue(r.get("flagged").asBoolean());

        // Two returns among many good orders stay under the threshold.
        long loyal = customer("Loyal");
        for (int day = 1; day <= 8; day++) order(loyal, "PAID", "2026-01-%02dT10:00:00".formatted(day));
        order(loyal, "RETURNED", "2026-01-10T10:00:00");
        order(loyal, "CANCELLED", "2026-01-11T10:00:00");
        JsonNode l = insights("Loyal");
        assertEquals(20, l.get("returnRatePct").asInt());
        assertFalse(l.get("flagged").asBoolean());
    }

    @Test
    void ordersCanBeListedForOneCustomer() throws Exception {
        long a = customer("A");
        long b = customer("B");
        order(a, "PAID", "2026-01-01T10:00:00");
        order(b, "PAID", "2026-01-02T10:00:00");
        order(b, "RETURNED", "2026-01-03T10:00:00");

        JsonNode rows = get(tenant, "/api/orders?customerId=" + b, 200);
        assertEquals(2, rows.get("totalElements").asLong());
        rows.get("content").forEach(o -> assertEquals("B", o.get("customerName").asText()));
        assertEquals(3, get(tenant, "/api/orders", 200).get("totalElements").asLong());
    }

    @Test
    void insightsOnlyCountTheBusinessesOwnOrders() throws Exception {
        long mine = customer("Mine");
        Tenant other = registerTenant();
        // Another business can't list or add orders against this customer.
        assertEquals(0, get(other, "/api/orders?customerId=" + mine, 200).get("totalElements").asLong());
        post(other, "/api/orders", """
                {"platformId":%d,"customerId":%d,"status":"RETURNED",
                 "items":[{"productId":%d,"quantity":1,"soldPrice":10}]}
                """.formatted(get(other, "/api/platforms", 200).get(0).get("id").asLong(), mine,
                post(other, "/api/products", "{\"name\":\"P\",\"costPrice\":1,\"sellingPrice\":10}", 201).get("id").asLong()),
                400);
        assertEquals(0, insights("Mine").get("orders").asLong());
    }
}
