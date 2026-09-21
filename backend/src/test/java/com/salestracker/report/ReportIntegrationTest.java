package com.salestracker.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.salestracker.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReportIntegrationTest extends AbstractIntegrationTest {

    private Tenant tenant;
    private long facebook;
    private long daraz;
    private long product;
    private long customer;

    /**
     * Product cost 100. Orders (all quantity 1):
     *   Aug 15  PAID     Daraz     sold 300  -> revenue 300, profit 200
     *   Sep 1   PAID     Facebook  sold 150  -> revenue 150, profit  50   (first instant of September)
     *   Sep 30  PAID     Daraz     sold 120  -> revenue 120, profit  20   (23:59 on the last day)
     *   Oct 1   PAID     Facebook  sold 500  -> revenue 500, profit 400   (00:00 next month: outside September)
     *   Sep 10  PENDING  Daraz     sold 200  -> expected 200 / profit 100
     *   Sep 11  RETURNED Facebook, Sep 12 CANCELLED Daraz
     */
    @BeforeEach
    void seed() throws Exception {
        tenant = registerTenant();
        JsonNode platforms = get(tenant, "/api/platforms", 200);
        for (JsonNode p : platforms) {
            if (p.get("name").asText().equals("Facebook Page")) facebook = p.get("id").asLong();
            if (p.get("name").asText().equals("Daraz")) daraz = p.get("id").asLong();
        }
        product = post(tenant, "/api/products",
                "{\"name\":\"Rice\",\"costPrice\":100,\"sellingPrice\":150}", 201).get("id").asLong();
        customer = post(tenant, "/api/customers", "{\"name\":\"Buyer\"}", 201).get("id").asLong();

        order(daraz, "PAID", "2026-08-15T10:00", 300);
        order(facebook, "PAID", "2026-09-01T00:00", 150);
        order(daraz, "PAID", "2026-09-30T23:59", 120);
        order(facebook, "PAID", "2026-10-01T00:00", 500);
        order(daraz, "PENDING", "2026-09-10T12:00", 200);
        order(facebook, "RETURNED", "2026-09-11T12:00", 150);
        order(daraz, "CANCELLED", "2026-09-12T12:00", 150);
    }

    private void order(long platformId, String status, String orderedAt, int soldPrice) throws Exception {
        post(tenant, "/api/orders", """
                {"platformId":%d,"customerId":%d,"status":"%s","orderedAt":"%s",
                 "items":[{"productId":%d,"quantity":1,"soldPrice":%d}]}
                """.formatted(platformId, customer, status, orderedAt, product, soldPrice), 201);
    }

    private JsonNode summary(String query) throws Exception {
        return get(tenant, "/api/reports/summary" + query, 200);
    }

    private static void assertMoney(String expected, JsonNode actual) {
        assertEquals(0, new java.math.BigDecimal(expected).compareTo(actual.decimalValue()),
                "expected " + expected + " but was " + actual);
    }

    @Test
    void septemberIncludesFirstInstantAndLastMinuteButNotNextMonth() throws Exception {
        JsonNode s = summary("?from=2026-09-01&to=2026-09-30");

        assertEquals(2, s.at("/realized/orders").asInt());
        assertMoney("270", s.at("/realized/revenue"));
        assertMoney("200", s.at("/realized/cost"));
        assertMoney("70", s.at("/realized/profit"));
        assertEquals(1, s.at("/pending/orders").asInt());
        assertMoney("200", s.at("/pending/revenue"));
        assertMoney("100", s.at("/pending/profit"));
        assertEquals(1, s.get("returnedOrders").asInt());
        assertEquals(1, s.get("cancelledOrders").asInt());
    }

    @Test
    void platformFilterCombinesWithDateRange() throws Exception {
        JsonNode s = summary("?from=2026-09-01&to=2026-09-30&platformId=" + daraz);

        assertEquals(1, s.at("/realized/orders").asInt());
        assertMoney("120", s.at("/realized/revenue"));
        assertMoney("20", s.at("/realized/profit"));
        assertEquals(1, s.get("byPlatform").size());
        assertEquals("Daraz", s.at("/byPlatform/0/platformName").asText());
        assertEquals(1, s.get("cancelledOrders").asInt());
        assertEquals(0, s.get("returnedOrders").asInt(), "the returned order is on Facebook");
    }

    @Test
    void openEndedRangeAndPlatformBreakdown() throws Exception {
        JsonNode all = summary("");
        assertEquals(4, all.at("/realized/orders").asInt());
        assertMoney("1070", all.at("/realized/revenue"));
        assertMoney("670", all.at("/realized/profit"));

        JsonNode rows = all.get("byPlatform");
        assertEquals(2, rows.size());
        assertEquals("Facebook Page", rows.get(0).get("platformName").asText(), "sorted by revenue desc");
        assertMoney("650", rows.get(0).get("revenue"));
        assertMoney("450", rows.get(0).get("profit"));
        assertEquals("Daraz", rows.get(1).get("platformName").asText());
        assertMoney("420", rows.get(1).get("revenue"));

        JsonNode fromOnly = summary("?from=2026-10-01");
        assertEquals(1, fromOnly.at("/realized/orders").asInt());
        assertMoney("500", fromOnly.at("/realized/revenue"));

        JsonNode toOnly = summary("?to=2026-08-31");
        assertEquals(1, toOnly.at("/realized/orders").asInt());
        assertMoney("300", toOnly.at("/realized/revenue"));
    }

    @Test
    void emptyRangeReturnsZerosNotErrors() throws Exception {
        JsonNode s = summary("?from=2030-01-01&to=2030-12-31");
        assertEquals(0, s.at("/realized/orders").asInt());
        assertMoney("0", s.at("/realized/profit"));
        assertEquals(0, s.get("byPlatform").size());
    }

    @Test
    void invalidInputIsRejectedWith400() throws Exception {
        get(tenant, "/api/reports/summary?from=2026-09-30&to=2026-09-01", 400);
        get(tenant, "/api/reports/summary?from=not-a-date", 400);
        get(tenant, "/api/reports/summary?platformId=999999", 400);
    }

    @Test
    void ordersListHonoursDateRange() throws Exception {
        JsonNode september = get(tenant, "/api/orders?from=2026-09-01&to=2026-09-30", 200);
        assertEquals(5, september.get("totalElements").asInt(), "Sep 1, 10, 11, 12 and 30");

        JsonNode darazSeptember = get(tenant, "/api/orders?from=2026-09-01&to=2026-09-30&platformId=" + daraz, 200);
        assertEquals(3, darazSeptember.get("totalElements").asInt());
    }

    @Test
    void anotherTenantSeesNothingAndCannotFilterByForeignPlatform() throws Exception {
        Tenant other = registerTenant();
        JsonNode s = get(other, "/api/reports/summary", 200);
        assertEquals(0, s.at("/realized/orders").asInt());
        assertEquals(0, s.get("byPlatform").size());

        get(other, "/api/reports/summary?platformId=" + daraz, 400);
        assertEquals(0, get(other, "/api/orders", 200).get("totalElements").asInt());
    }
}
