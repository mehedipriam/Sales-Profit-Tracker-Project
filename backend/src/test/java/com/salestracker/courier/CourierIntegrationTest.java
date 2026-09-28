package com.salestracker.courier;

import com.fasterxml.jackson.databind.JsonNode;
import com.salestracker.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CourierIntegrationTest extends AbstractIntegrationTest {

    private Tenant tenant;
    private long facebook;
    private long pathao;
    private long customer;
    private long rice; // cost 100

    @BeforeEach
    void setUp() throws Exception {
        tenant = registerTenant();
        facebook = get(tenant, "/api/platforms", 200).get(0).get("id").asLong();
        pathao = courierId(tenant, "Pathao");
        customer = post(tenant, "/api/customers", "{\"name\":\"Buyer\"}", 201).get("id").asLong();
        rice = post(tenant, "/api/products", "{\"name\":\"Rice\",\"costPrice\":100,\"sellingPrice\":150}", 201).get("id").asLong();
    }

    // ---- helpers ----

    private long courierId(Tenant t, String name) throws Exception {
        for (JsonNode c : get(t, "/api/couriers", 200)) {
            if (c.get("name").asText().equals(name)) return c.get("id").asLong();
        }
        throw new AssertionError("No courier " + name);
    }

    /** Two units at 150 (revenue 300) plus a 60 delivery charge: the customer pays 360. */
    private String orderBody(String status, Long courierId, String tracking, String codAmount) {
        return """
                {"platformId":%d,"customerId":%d,"status":"%s","deliveryCharge":60,
                 "courierId":%s,"trackingNumber":%s,"codAmount":%s,
                 "items":[{"productId":%d,"quantity":2,"soldPrice":150}]}
                """.formatted(facebook, customer, status, courierId, tracking == null ? "null" : "\"" + tracking + "\"",
                codAmount, rice);
    }

    private long order(String status, Long courierId, String codAmount) throws Exception {
        return post(tenant, "/api/orders", orderBody(status, courierId, null, codAmount), 201).get("id").asLong();
    }

    private JsonNode payouts() throws Exception {
        return get(tenant, "/api/couriers/payouts", 200);
    }

    private void markPaid(String paidOn, long... ids) throws Exception {
        post(tenant, "/api/couriers/payouts", "{\"orderIds\":%s,\"paidOn\":\"%s\"}".formatted(ids(ids), paidOn), 204);
    }

    private static String ids(long... ids) {
        List<String> s = new ArrayList<>();
        for (long id : ids) s.add(String.valueOf(id));
        return "[" + String.join(",", s) + "]";
    }

    private static List<Long> orderIds(JsonNode rows) {
        List<Long> result = new ArrayList<>();
        rows.forEach(r -> result.add(r.get("id").asLong()));
        return result;
    }

    private static void assertMoney(String expected, JsonNode actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual.decimalValue()), "expected " + expected + " but was " + actual);
    }

    // ---- the courier list ----

    @Test
    void aNewWorkspaceStartsWithCommonCouriersAndCanAddItsOwn() throws Exception {
        List<String> names = new ArrayList<>();
        get(tenant, "/api/couriers", 200).forEach(c -> names.add(c.get("name").asText()));
        assertEquals(List.of("Paperfly", "Pathao", "RedX", "Steadfast", "Sundarban Courier"), names);

        JsonNode karatoa = post(tenant, "/api/couriers",
                "{\"name\":\"Karatoa Courier\",\"trackingUrl\":\"https://example.com/track?id={tracking}\"}", 201);
        assertEquals("https://example.com/track?id={tracking}", karatoa.get("trackingUrl").asText());
        post(tenant, "/api/couriers", "{\"name\":\"Karatoa Courier\"}", 409);

        // Tracking links are rendered as links, so only http(s) is accepted.
        post(tenant, "/api/couriers", "{\"name\":\"Evil\",\"trackingUrl\":\"javascript:alert(1)\"}", 400);

        // Removing a courier hides it; adding the same name again brings the same courier back.
        long id = karatoa.get("id").asLong();
        delete(tenant, "/api/couriers/" + id, 204);
        assertThrows(AssertionError.class, () -> courierId(tenant, "Karatoa Courier"));
        assertEquals(id, post(tenant, "/api/couriers", "{\"name\":\"Karatoa Courier\"}", 201).get("id").asLong());
    }

    @Test
    void couriersAreSeparatePerBusiness() throws Exception {
        Tenant other = registerTenant();
        long theirs = courierId(other, "Pathao");
        assertNotEquals(pathao, theirs);
        post(tenant, "/api/orders", orderBody("PENDING", theirs, null, "null"), 400);
        put(tenant, "/api/couriers/" + theirs, "{\"name\":\"Mine now\"}", 404);

        long theirOrder = post(other, "/api/orders", """
                {"platformId":%d,"customerId":%d,"status":"PAID","courierId":%d,
                 "items":[{"productId":%d,"quantity":1,"soldPrice":10}]}
                """.formatted(get(other, "/api/platforms", 200).get(0).get("id").asLong(),
                post(other, "/api/customers", "{\"name\":\"X\"}", 201).get("id").asLong(), theirs,
                post(other, "/api/products", "{\"name\":\"P\",\"costPrice\":1,\"sellingPrice\":10}", 201).get("id").asLong()),
                201).get("id").asLong();
        post(tenant, "/api/couriers/payouts", "{\"orderIds\":[%d]}".formatted(theirOrder), 404);
        assertEquals(0, payouts().get("dueOrders").asLong());
    }

    // ---- courier details on an order ----

    @Test
    void anOrderCarriesItsCourierTrackingAndCashToCollect() throws Exception {
        put(tenant, "/api/couriers/" + pathao,
                "{\"name\":\"Pathao\",\"trackingUrl\":\"https://example.com/t/{tracking}\"}", 200);
        JsonNode o = post(tenant, "/api/orders", orderBody("PENDING", pathao, " DA 12/34 ", "null"), 201);
        JsonNode courier = o.get("courier");
        assertEquals("Pathao", courier.get("courierName").asText());
        assertEquals("DA 12/34", courier.get("trackingNumber").asText());
        assertEquals("https://example.com/t/DA+12%2F34", courier.get("trackingLink").asText());
        assertMoney("360", courier.get("codAmount")); // no amount given: products + delivery charge
        assertTrue(courier.get("paidOn").isNull());

        JsonNode listed = get(tenant, "/api/orders", 200).at("/content/0/courier");
        assertEquals("Pathao", listed.get("courierName").asText());

        // The parcel / consignment ID is kept on its own, and fills the courier's link when there is no tracking code.
        JsonNode parcel = post(tenant, "/api/orders", """
                {"platformId":%d,"customerId":%d,"status":"PENDING","courierId":%d,"consignmentId":" CN-778 ",
                 "items":[{"productId":%d,"quantity":1,"soldPrice":150}]}
                """.formatted(facebook, customer, pathao, rice), 201).get("courier");
        assertEquals("CN-778", parcel.get("consignmentId").asText());
        assertTrue(parcel.get("trackingNumber").isNull());
        assertEquals("https://example.com/t/CN-778", parcel.get("trackingLink").asText());
        assertTrue(courier.get("consignmentId").isNull());

                // A pasted tracking link is used as the link itself; anything that isn't http(s) stays plain text.
        long linked = post(tenant, "/api/orders",
                orderBody("PENDING", courierId(tenant, "RedX"), "https://redx.example/track/ABC-1", "null"), 201).get("id").asLong();
        JsonNode linkedCourier = get(tenant, "/api/orders/" + linked, 200).get("courier");
        assertEquals("https://redx.example/track/ABC-1", linkedCourier.get("trackingNumber").asText());
        assertEquals("https://redx.example/track/ABC-1", linkedCourier.get("trackingLink").asText());
        JsonNode sneaky = post(tenant, "/api/orders",
                orderBody("PENDING", courierId(tenant, "RedX"), "javascript:alert(1)", "null"), 201).get("courier");
        assertTrue(sneaky.get("trackingLink").isNull());

        // Paid in advance: nothing for the courier to collect.
        long id = o.get("id").asLong();
        assertMoney("0", put(tenant, "/api/orders/" + id, orderBody("PENDING", pathao, null, "0"), 200).at("/courier/codAmount"));

        // No courier: the courier block goes away entirely.
        assertTrue(put(tenant, "/api/orders/" + id, orderBody("PENDING", null, "X1", "100"), 200).get("courier").isNull());
    }

    // ---- payouts ----

    @Test
    void cashMovesFromOnTheWayToDueToReceived() throws Exception {
        long redx = courierId(tenant, "RedX");
        long onTheWay = order("PENDING", pathao, "null");       // 360 still to be collected
        long delivered = order("PAID", pathao, "null");         // 360 collected, not paid out yet
        long partial = order("PAID", redx, "200");              // part paid in advance, 200 collected
        order("PAID", redx, "0");                               // fully prepaid: nothing owed
        order("PAID", null, "null");                            // no courier
        long returned = order("RETURNED", pathao, "null");      // parcel came back: no cash

        JsonNode p = payouts();
        assertMoney("560", p.get("dueTotal"));
        assertEquals(2, p.get("dueOrders").asLong());
        assertMoney("360", p.get("transitTotal"));
        assertEquals(1, p.get("transitOrders").asLong());
        assertEquals(List.of(delivered, partial), orderIds(p.get("due")));
        JsonNode first = p.get("couriers").get(0);
        assertEquals("Pathao", first.get("name").asText());
        assertMoney("360", first.get("dueAmount"));
        assertMoney("360", first.get("transitAmount"));
        assertEquals(5, p.get("couriers").size()); // every active courier gets a row, owing or not

        assertEquals(List.of(partial), orderIds(get(tenant, "/api/couriers/payouts?courierId=" + redx, 200).get("due")));

        JsonNode dash = get(tenant, "/api/dashboard", 200).get("withCouriers");
        assertEquals(2, dash.get("orders").asLong());
        assertMoney("560", dash.get("amount"));

        // Pathao pays out: its delivered order and the one still marked pending (it must have been delivered).
        markPaid("2026-09-20", delivered, onTheWay);
        p = payouts();
        assertEquals(List.of(partial), orderIds(p.get("due")));
        assertMoney("200", p.get("dueTotal"));
        assertMoney("0", p.get("transitTotal"));
        assertEquals("PAID", get(tenant, "/api/orders/" + onTheWay, 200).get("status").asText());
        assertEquals("2026-09-20", get(tenant, "/api/orders/" + delivered, 200).at("/courier/paidOn").asText());
        assertEquals(List.of(delivered, onTheWay), orderIds(p.get("recent"))); // same day: newest order first
        assertMoney("200", get(tenant, "/api/dashboard", 200).at("/withCouriers/amount"));

        // Nothing is owed twice, and a returned parcel never had cash to pay out.
        post(tenant, "/api/couriers/payouts", "{\"orderIds\":[%d]}".formatted(delivered), 400);
        post(tenant, "/api/couriers/payouts", "{\"orderIds\":[%d]}".formatted(returned), 400);

        // Undo a payout recorded by mistake.
        post(tenant, "/api/couriers/payouts/undo", "{\"orderIds\":[%d]}".formatted(delivered), 204);
        assertEquals(List.of(delivered, partial), orderIds(payouts().get("due")));
    }

    @Test
    void switchingTheCourierForgetsTheOldPayout() throws Exception {
        long id = order("PAID", pathao, "null");
        markPaid("2026-09-21", id);
        // Editing other details keeps the payout...
        assertEquals("2026-09-21", put(tenant, "/api/orders/" + id, orderBody("PAID", pathao, "T-1", "null"), 200)
                .at("/courier/paidOn").asText());
        // ...but a different courier is a different parcel.
        long redx = courierId(tenant, "RedX");
        assertTrue(put(tenant, "/api/orders/" + id, orderBody("PAID", redx, "T-1", "null"), 200)
                .at("/courier/paidOn").isNull());
    }

    @Test
    void staffManageCouriersButNotPayouts() throws Exception {
        String email = "staff%d@example.com".formatted(System.nanoTime());
        post(tenant, "/api/users",
                "{\"fullName\":\"Staff\",\"email\":\"%s\",\"password\":\"password123\",\"role\":\"STAFF\"}".formatted(email), 201);
        JsonNode login = postAnonymous("/api/auth/login", "{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email), 200);
        Tenant staff = new Tenant(login.at("/user/tenantId").asLong(), "Bearer " + login.get("token").asText());

        post(staff, "/api/couriers", "{\"name\":\"eCourier\"}", 201);
        long id = post(staff, "/api/orders", orderBody("PAID", pathao, "S-1", "null"), 201).get("id").asLong();
        assertEquals("S-1", get(staff, "/api/orders/" + id, 200).at("/courier/trackingNumber").asText());

        get(staff, "/api/couriers/payouts", 403);
        post(staff, "/api/couriers/payouts", "{\"orderIds\":[%d]}".formatted(id), 403);
        post(staff, "/api/couriers/payouts/undo", "{\"orderIds\":[%d]}".formatted(id), 403);
    }
}
