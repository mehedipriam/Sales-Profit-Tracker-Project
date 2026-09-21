package com.salestracker.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.salestracker.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ReportChartsIntegrationTest extends AbstractIntegrationTest {

    private Tenant tenant;
    private long facebook;
    private long daraz;
    private long customer;
    private long rice;
    private long oil;
    private long salt;

    /**
     * Costs: Rice 100, Oil 50, Salt 30. Paid orders:
     *   Jan 10 Facebook  Rice x2 @150 + Oil x1 @40   -> revenue 340, cost 250, profit  90
     *   Jan 20 Daraz     Rice x1 @90                 -> revenue  90, cost 100, profit -10
     *   Jan 25 Facebook  Salt x5 @20                 -> revenue 100, cost 150, profit -50
     *   Mar 05 Facebook  Oil x10 @60                 -> revenue 600, cost 500, profit 100
     * Not paid (must never show up): Jan 12 PENDING Oil x100 @1, Feb 10 CANCELLED Rice, Feb 11 RETURNED Rice.
     */
    private void seed() throws Exception {
        tenant = registerTenant();
        for (JsonNode p : get(tenant, "/api/platforms", 200)) {
            if (p.get("name").asText().equals("Facebook Page")) facebook = p.get("id").asLong();
            if (p.get("name").asText().equals("Daraz")) daraz = p.get("id").asLong();
        }
        customer = post(tenant, "/api/customers", "{\"name\":\"Buyer\"}", 201).get("id").asLong();
        rice = product("Rice", 100);
        oil = product("Oil", 50);
        salt = product("Salt", 30);

        order(facebook, "PAID", "2026-01-10T10:00", item(rice, 2, 150), item(oil, 1, 40));
        order(daraz, "PAID", "2026-01-20T10:00", item(rice, 1, 90));
        order(facebook, "PAID", "2026-01-25T10:00", item(salt, 5, 20));
        order(facebook, "PAID", "2026-03-05T10:00", item(oil, 10, 60));
        order(facebook, "PENDING", "2026-01-12T10:00", item(oil, 100, 1));
        order(facebook, "CANCELLED", "2026-02-10T10:00", item(rice, 1, 150));
        order(facebook, "RETURNED", "2026-02-11T10:00", item(rice, 1, 150));
    }

    private long product(String name, int cost) throws Exception {
        return post(tenant, "/api/products",
                "{\"name\":\"%s\",\"costPrice\":%d,\"sellingPrice\":%d}".formatted(name, cost, cost * 2), 201)
                .get("id").asLong();
    }

    private static String item(long productId, int quantity, int soldPrice) {
        return "{\"productId\":%d,\"quantity\":%d,\"soldPrice\":%d}".formatted(productId, quantity, soldPrice);
    }

    private void order(long platformId, String status, String orderedAt, String... items) throws Exception {
        post(tenant, "/api/orders", """
                {"platformId":%d,"customerId":%d,"status":"%s","orderedAt":"%s","items":[%s]}
                """.formatted(platformId, customer, status, orderedAt, String.join(",", items)), 201);
    }

    private static void assertMoney(String expected, JsonNode actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual.decimalValue()),
                "expected " + expected + " but was " + actual);
    }

    // ---- trend ----

    @Test
    void longRangeIsChartedPerMonthWithEmptyMonthsFilledWithZero() throws Exception {
        seed();
        JsonNode t = get(tenant, "/api/reports/trend?from=2026-01-01&to=2026-03-31", 200);

        assertEquals("month", t.get("granularity").asText());
        JsonNode p = t.get("points");
        assertEquals(3, p.size());

        assertEquals("2026-01-01", p.get(0).get("period").asText());
        assertEquals(3, p.get(0).get("orders").asInt());
        assertMoney("530", p.get(0).get("revenue"));
        assertMoney("500", p.get(0).get("cost"));
        assertMoney("30", p.get(0).get("profit"));

        assertEquals("2026-02-01", p.get(1).get("period").asText());
        assertEquals(0, p.get(1).get("orders").asInt(), "February has only cancelled/returned orders");
        assertMoney("0", p.get(1).get("revenue"));
        assertMoney("0", p.get(1).get("profit"));

        assertEquals("2026-03-01", p.get(2).get("period").asText());
        assertMoney("600", p.get(2).get("revenue"));
        assertMoney("100", p.get(2).get("profit"));
    }

    @Test
    void shortRangeIsChartedPerDayIncludingNegativeProfit() throws Exception {
        seed();
        JsonNode t = get(tenant, "/api/reports/trend?from=2026-01-01&to=2026-01-31", 200);

        assertEquals("day", t.get("granularity").asText());
        JsonNode p = t.get("points");
        assertEquals(31, p.size());
        assertEquals("2026-01-01", p.get(0).get("period").asText());

        JsonNode jan10 = p.get(9);
        assertEquals("2026-01-10", jan10.get("period").asText());
        assertMoney("340", jan10.get("revenue"));
        assertMoney("250", jan10.get("cost"));
        assertMoney("90", jan10.get("profit"));

        assertMoney("-10", p.get(19).get("profit"));
        assertMoney("-50", p.get(24).get("profit"));
        assertMoney("0", p.get(10).get("revenue"));
        assertEquals(0, p.get(11).get("orders").asInt(), "the pending order on Jan 12 is not realized");
    }

    @Test
    void dailyBecomesMonthlyExactlyAfter62Days() throws Exception {
        seed();
        assertEquals("day", get(tenant, "/api/reports/trend?from=2026-01-01&to=2026-03-03", 200).get("granularity").asText());
        assertEquals(62, get(tenant, "/api/reports/trend?from=2026-01-01&to=2026-03-03", 200).get("points").size());
        assertEquals("month", get(tenant, "/api/reports/trend?from=2026-01-01&to=2026-03-04", 200).get("granularity").asText());
    }

    @Test
    void openEndedRangeSpansFirstToLastDayWithData() throws Exception {
        seed();
        JsonNode t = get(tenant, "/api/reports/trend", 200);
        assertEquals("day", t.get("granularity").asText(), "Jan 10 to Mar 5 is 55 days");
        assertEquals(55, t.get("points").size());
        assertEquals("2026-01-10", t.at("/points/0/period").asText());
        assertEquals("2026-03-05", t.at("/points/54/period").asText());
    }

    @Test
    void trendHonoursPlatformFilter() throws Exception {
        seed();
        JsonNode t = get(tenant, "/api/reports/trend?from=2026-01-01&to=2026-03-31&platformId=" + daraz, 200);
        JsonNode p = t.get("points");
        assertMoney("90", p.get(0).get("revenue"));
        assertMoney("-10", p.get(0).get("profit"));
        assertMoney("0", p.get(2).get("revenue"), "Daraz sold nothing in March");
    }

    private static void assertMoney(String expected, JsonNode actual, String message) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual.decimalValue()), message);
    }

    @Test
    void emptyBusinessGetsEmptyOrZeroFilledTrend() throws Exception {
        Tenant empty = registerTenant();
        assertEquals(0, get(empty, "/api/reports/trend", 200).get("points").size());

        JsonNode bounded = get(empty, "/api/reports/trend?from=2026-01-01&to=2026-01-05", 200);
        assertEquals(5, bounded.get("points").size());
        assertMoney("0", bounded.at("/points/0/revenue"));
    }

    // ---- products ----

    @Test
    void topSellersRankByUnitsAndIgnoreUnpaidOrders() throws Exception {
        seed();
        JsonNode s = get(tenant, "/api/reports/products", 200).get("topSellers");

        assertEquals(3, s.size());
        assertEquals("Oil", s.get(0).get("name").asText());
        assertEquals(11, s.get(0).get("quantity").asInt(), "1 + 10 paid units; the 100 pending units don't count");
        assertMoney("640", s.get(0).get("revenue"));
        assertMoney("90", s.get(0).get("profit"));
        assertMoney("14.06", s.get(0).get("marginPct"));
        assertEquals("Salt", s.get(1).get("name").asText());
        assertEquals(5, s.get(1).get("quantity").asInt());
        assertEquals("Rice", s.get(2).get("name").asText());
        assertEquals(3, s.get(2).get("quantity").asInt(), "cancelled and returned rice doesn't count");
    }

    @Test
    void lowestMarginPutsLossMakingProductsFirst() throws Exception {
        seed();
        JsonNode m = get(tenant, "/api/reports/products", 200).get("lowestMargin");

        assertEquals("Salt", m.get(0).get("name").asText());
        assertMoney("-50", m.get(0).get("profit"));
        assertMoney("-50.00", m.get(0).get("marginPct"));
        assertEquals("Oil", m.get(1).get("name").asText());
        assertEquals("Rice", m.get(2).get("name").asText());
        assertMoney("23.08", m.get(2).get("marginPct"));
    }

    @Test
    void productsHonourPlatformAndDateFilters() throws Exception {
        seed();
        JsonNode daraz1 = get(tenant, "/api/reports/products?platformId=" + daraz, 200);
        assertEquals(1, daraz1.get("topSellers").size());
        assertEquals("Rice", daraz1.at("/topSellers/0/name").asText());
        assertEquals(1, daraz1.at("/topSellers/0/quantity").asInt());
        assertMoney("-11.11", daraz1.at("/lowestMargin/0/marginPct"));

        JsonNode march = get(tenant, "/api/reports/products?from=2026-03-01&to=2026-03-31", 200);
        assertEquals(1, march.get("topSellers").size());
        assertEquals("Oil", march.at("/topSellers/0/name").asText());
        assertEquals(10, march.at("/topSellers/0/quantity").asInt());
    }

    @Test
    void listsAreCappedAtTenAndGiveawaysRankWorstWithNoMargin() throws Exception {
        tenant = registerTenant();
        for (JsonNode p : get(tenant, "/api/platforms", 200)) facebook = p.get("id").asLong();
        customer = post(tenant, "/api/customers", "{\"name\":\"Buyer\"}", 201).get("id").asLong();

        for (int i = 1; i <= 12; i++) {
            long id = product("P" + i, 10);
            order(facebook, "PAID", "2026-05-01T10:00", item(id, i, 20));
        }
        long gift = product("Gift", 10);
        order(facebook, "PAID", "2026-05-01T10:00", item(gift, 1, 0));

        JsonNode r = get(tenant, "/api/reports/products", 200);
        JsonNode top = r.get("topSellers");
        assertEquals(10, top.size());
        assertEquals(12, top.get(0).get("quantity").asInt());
        assertEquals(3, top.get(9).get("quantity").asInt(), "P1, P2 and Gift fall outside the top 10");

        JsonNode low = r.get("lowestMargin");
        assertEquals(10, low.size());
        assertEquals("Gift", low.get(0).get("name").asText(), "sold at 0 -> worst possible margin");
        assertTrue(low.get(0).get("marginPct").isNull(), "no revenue means no margin percentage");
        assertMoney("-10", low.get(0).get("profit"));
    }

    // ---- shared guarantees ----

    @Test
    void invalidInputIsRejectedAndOtherBusinessesSeeNothing() throws Exception {
        seed();
        for (String endpoint : new String[]{"/api/reports/trend", "/api/reports/products"}) {
            get(tenant, endpoint + "?from=2026-03-01&to=2026-01-01", 400);
            get(tenant, endpoint + "?platformId=999999", 400);
            get(tenant, endpoint + "?from=nope", 400);
        }

        Tenant other = registerTenant();
        assertEquals(0, get(other, "/api/reports/trend", 200).get("points").size());
        assertEquals(0, get(other, "/api/reports/products", 200).get("topSellers").size());
        get(other, "/api/reports/trend?platformId=" + daraz, 400);
        get(other, "/api/reports/products?platformId=" + daraz, 400);
    }
}
