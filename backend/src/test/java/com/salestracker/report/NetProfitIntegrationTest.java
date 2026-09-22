package com.salestracker.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.salestracker.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** Gross profit (revenue - cost of goods) versus net profit (gross - expenses). */
class NetProfitIntegrationTest extends AbstractIntegrationTest {

    private Tenant tenant;
    private long facebook;
    private long daraz;
    private long customer;
    private long rice; // cost 100

    @BeforeEach
    void setUp() throws Exception {
        tenant = registerTenant();
        for (JsonNode p : get(tenant, "/api/platforms", 200)) {
            if (p.get("name").asText().equals("Facebook Page")) facebook = p.get("id").asLong();
            if (p.get("name").asText().equals("Daraz")) daraz = p.get("id").asLong();
        }
        customer = post(tenant, "/api/customers", "{\"name\":\"Buyer\"}", 201).get("id").asLong();
        rice = post(tenant, "/api/products", "{\"name\":\"Rice\",\"costPrice\":100,\"sellingPrice\":150}", 201).get("id").asLong();
        // Facebook charges 10%
        put(tenant, "/api/platforms/" + facebook, "{\"name\":\"Facebook Page\",\"commissionPct\":10}", 200);
    }

    // ---- helpers ----

    /** Two units at 150 each: revenue 300, cost 200, gross profit 100. */
    private long order(long platformId, String status, String date) throws Exception {
        return post(tenant, "/api/orders", """
                {"platformId":%d,"customerId":%d,"status":"%s","orderedAt":"%sT12:00:00",
                 "items":[{"productId":%d,"quantity":2,"soldPrice":150}]}
                """.formatted(platformId, customer, status, date, rice), 201).get("id").asLong();
    }

    private void expense(String type, String amount, String date, Long orderId) throws Exception {
        post(tenant, "/api/expenses", """
                {"type":"%s","amount":%s,"expenseDate":"%s","orderId":%s}
                """.formatted(type, amount, date, orderId == null ? "null" : orderId), 201);
    }

    private JsonNode summary(String query) throws Exception {
        return get(tenant, "/api/reports/summary" + query, 200);
    }

    private static void assertMoney(String expected, JsonNode actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual.decimalValue()), "expected " + expected + " but was " + actual);
    }

    private static JsonNode row(JsonNode summary, String platform) {
        for (JsonNode r : summary.get("byPlatform")) {
            if (r.get("platformName").asText().equals(platform)) return r;
        }
        throw new AssertionError("no row for " + platform);
    }

    // ---- summary ----

    @Test
    void netProfitSubtractsCommissionOrderExpensesAndOverhead() throws Exception {
        long o = order(facebook, "PAID", "2026-09-10");      // gross 100, commission 30
        expense("DELIVERY", "20", "2026-09-10", o);         // tied to the order
        expense("ADS", "15", "2026-09-12", null);            // overhead, no order

        JsonNode s = summary("?from=2026-09-01&to=2026-09-30");
        assertMoney("100", s.get("realized").get("profit"));   // gross is unchanged
        assertMoney("65", s.get("expenses").get("total"));
        assertMoney("15", s.get("expenses").get("unallocated"));
        assertMoney("35", s.get("netProfit"));

        JsonNode byType = s.get("expenses").get("byType");
        assertEquals(3, byType.size());
        assertEquals("PLATFORM_COMMISSION", byType.get(0).get("type").asText()); // largest first
        assertMoney("30", byType.get(0).get("total"));

        JsonNode fb = row(s, "Facebook Page");
        assertMoney("100", fb.get("profit"));
        assertMoney("50", fb.get("expenses"));
        assertMoney("50", fb.get("netProfit"));
    }

    @Test
    void platformFilterLeavesOutOverheadThatBelongsToNoPlatform() throws Exception {
        long fb = order(facebook, "PAID", "2026-09-10");
        order(daraz, "PAID", "2026-09-11");                  // Daraz has no commission set
        expense("DELIVERY", "20", "2026-09-10", fb);
        expense("ADS", "15", "2026-09-12", null);

        JsonNode all = summary("?from=2026-09-01&to=2026-09-30");
        assertMoney("200", all.get("realized").get("profit"));
        assertMoney("65", all.get("expenses").get("total"));
        assertMoney("135", all.get("netProfit"));

        JsonNode onlyFb = summary("?from=2026-09-01&to=2026-09-30&platformId=" + facebook);
        assertMoney("50", onlyFb.get("expenses").get("total"));
        assertMoney("0", onlyFb.get("expenses").get("unallocated"));
        assertMoney("50", onlyFb.get("netProfit"));

        JsonNode onlyDaraz = summary("?from=2026-09-01&to=2026-09-30&platformId=" + daraz);
        assertMoney("0", onlyDaraz.get("expenses").get("total"));
        assertMoney("100", onlyDaraz.get("netProfit"));
    }

    @Test
    void expensesOnPendingOrdersCountOnlyOnceThePaymentArrives() throws Exception {
        long o = order(facebook, "PENDING", "2026-09-10");
        expense("DELIVERY", "20", "2026-09-10", o);

        JsonNode pending = summary("?from=2026-09-01&to=2026-09-30");
        assertMoney("0", pending.get("realized").get("profit"));
        assertMoney("0", pending.get("expenses").get("total")); // commission and delivery both wait
        assertMoney("0", pending.get("netProfit"));

        patch(tenant, "/api/orders/" + o + "/status", "{\"status\":\"PAID\"}", 200);
        JsonNode paid = summary("?from=2026-09-01&to=2026-09-30");
        assertMoney("100", paid.get("realized").get("profit"));
        assertMoney("50", paid.get("expenses").get("total"));
        assertMoney("50", paid.get("netProfit"));
    }

    @Test
    void aReturnedOrdersDeliveryCostIsARealLossButItsCommissionIsRefunded() throws Exception {
        long o = order(facebook, "PAID", "2026-09-10");
        expense("DELIVERY", "20", "2026-09-10", o);
        patch(tenant, "/api/orders/" + o + "/status", "{\"status\":\"RETURNED\"}", 200);

        JsonNode s = summary("?from=2026-09-01&to=2026-09-30");
        assertMoney("0", s.get("realized").get("profit"));
        assertMoney("20", s.get("expenses").get("total"));   // delivery stays, commission is gone
        assertMoney("-20", s.get("netProfit"));
        // Facebook has no paid orders but does have the loss, so it still gets a row.
        JsonNode fb = row(s, "Facebook Page");
        assertEquals(0, fb.get("orders").asInt());
        assertMoney("-20", fb.get("netProfit"));
    }

    @Test
    void onlyExpensesInsideTheRangeCount() throws Exception {
        long o = order(facebook, "PAID", "2026-09-10");
        expense("DELIVERY", "20", "2026-08-31", o);
        expense("ADS", "15", "2026-10-01", null);

        JsonNode s = summary("?from=2026-09-01&to=2026-09-30");
        assertMoney("30", s.get("expenses").get("total"));   // just the commission
        assertMoney("70", s.get("netProfit"));
    }

    @Test
    void grossAndNetAreEqualWhenThereAreNoExpenses() throws Exception {
        order(daraz, "PAID", "2026-09-10");
        JsonNode s = summary("");
        assertMoney("100", s.get("realized").get("profit"));
        assertMoney("0", s.get("expenses").get("total"));
        assertMoney("100", s.get("netProfit"));
        assertEquals(0, s.get("expenses").get("byType").size());
    }

    // ---- trend and dashboard ----

    @Test
    void trendCarriesExpensesAndNetPerDay() throws Exception {
        long o = order(facebook, "PAID", "2026-09-02");
        expense("DELIVERY", "20", "2026-09-02", o);
        expense("ADS", "15", "2026-09-04", null);          // a day with no sales still shows its spend

        JsonNode points = get(tenant, "/api/reports/trend?from=2026-09-01&to=2026-09-05", 200).get("points");
        assertEquals(5, points.size());
        JsonNode sale = points.get(1);
        assertMoney("100", sale.get("profit"));
        assertMoney("50", sale.get("expenses"));
        assertMoney("50", sale.get("netProfit"));
        JsonNode adsOnly = points.get(3);
        assertMoney("0", adsOnly.get("profit"));
        assertMoney("-15", adsOnly.get("netProfit"));
    }

    @Test
    void monthlyTrendRollsExpensesUpAndOpenRangesIncludeExpenseOnlyMonths() throws Exception {
        order(daraz, "PAID", "2026-06-10");                  // gross 100, no commission
        expense("ADS", "40", "2026-08-15", null);            // a month with spend and no sales

        JsonNode t = get(tenant, "/api/reports/trend", 200);
        assertEquals("month", t.get("granularity").asText());
        JsonNode points = t.get("points");
        assertEquals(3, points.size());                      // June, July, August
        assertMoney("100", points.get(0).get("netProfit"));
        assertMoney("0", points.get(1).get("netProfit"));
        assertMoney("-40", points.get(2).get("netProfit"));
        assertMoney("40", points.get(2).get("expenses"));
    }

    @Test
    void dashboardShowsAllTimeNetProfit() throws Exception {
        long o = order(facebook, "PAID", "2026-09-10");
        expense("DELIVERY", "20", "2026-09-10", o);
        order(facebook, "PENDING", "2026-09-11");           // its commission is expected, not yet counted

        JsonNode d = get(tenant, "/api/dashboard", 200);
        assertMoney("100", d.get("realized").get("profit"));
        assertMoney("50", d.get("expenses"));
        assertMoney("50", d.get("netProfit"));
    }

    // ---- delivery the customer pays ----

    /** Like order(), with the customer paying a delivery charge on top of the products. */
    private long orderWithDelivery(long platformId, String status, String date, String deliveryCharge) throws Exception {
        return post(tenant, "/api/orders", """
                {"platformId":%d,"customerId":%d,"status":"%s","orderedAt":"%sT12:00:00","deliveryCharge":%s,
                 "items":[{"productId":%d,"quantity":2,"soldPrice":150}]}
                """.formatted(platformId, customer, status, date, deliveryCharge, rice), 201).get("id").asLong();
    }

    @Test
    void deliveryTheCustomerPaysOffsetsTheDeliveryExpense() throws Exception {
        long o = orderWithDelivery(daraz, "PAID", "2026-09-10", "20");  // gross 100, customer paid 20 delivery
        expense("DELIVERY", "20", "2026-09-10", o);                     // courier cost 20

        JsonNode s = summary("?from=2026-09-01&to=2026-09-30");
        assertMoney("100", s.get("realized").get("profit"));   // product profit is untouched
        assertMoney("20", s.get("realized").get("delivery"));
        assertMoney("20", s.get("expenses").get("total"));
        assertMoney("100", s.get("netProfit"));                // the delivery washes out

        JsonNode daraz = row(s, "Daraz");
        assertMoney("20", daraz.get("delivery"));
        assertMoney("100", daraz.get("netProfit"));

        JsonNode point = get(tenant, "/api/reports/trend?from=2026-09-10&to=2026-09-10", 200).get("points").get(0);
        assertMoney("20", point.get("delivery"));
        assertMoney("100", point.get("netProfit"));

        JsonNode d = get(tenant, "/api/dashboard", 200);
        assertMoney("20", d.get("realized").get("delivery"));
        assertMoney("100", d.get("netProfit"));

        JsonNode detail = get(tenant, "/api/orders/" + o, 200);
        assertMoney("20", detail.get("deliveryCharge"));
        assertMoney("300", detail.get("revenue"));             // revenue stays the products' price
    }

    @Test
    void chargingMoreThanTheCourierCostsIsProfitAndCommissionIgnoresDelivery() throws Exception {
        long o = orderWithDelivery(facebook, "PAID", "2026-09-10", "30"); // gross 100, commission 10% of 300 = 30
        expense("DELIVERY", "20", "2026-09-10", o);

        JsonNode s = summary("?from=2026-09-01&to=2026-09-30");
        assertMoney("50", s.get("expenses").get("total"));     // commission is on the products, not the delivery
        assertMoney("80", s.get("netProfit"));                 // 100 + 30 - 20 - 30
    }

    @Test
    void deliveryOnUnpaidOrdersIsNotIncome() throws Exception {
        orderWithDelivery(daraz, "PENDING", "2026-09-10", "20");
        long returned = orderWithDelivery(daraz, "PAID", "2026-09-11", "20");
        patch(tenant, "/api/orders/" + returned + "/status", "{\"status\":\"RETURNED\"}", 200);

        JsonNode s = summary("?from=2026-09-01&to=2026-09-30");
        assertMoney("0", s.get("realized").get("delivery"));
        assertMoney("20", s.get("pending").get("delivery"));   // expected along with the pending sale
        assertMoney("0", s.get("netProfit"));
    }
}
