package com.salestracker.activity;

import com.fasterxml.jackson.databind.JsonNode;
import com.salestracker.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderActivityIntegrationTest extends AbstractIntegrationTest {

    private Tenant owner;
    private Tenant staff;
    private long staffId;
    private long facebook;
    private long customer;
    private long rice;

    @BeforeEach
    void setUp() throws Exception {
        owner = registerTenant();
        String email = "staff%d@example.com".formatted(System.nanoTime());
        staffId = post(owner, "/api/users",
                "{\"fullName\":\"Rina\",\"email\":\"%s\",\"password\":\"password123\",\"role\":\"STAFF\"}".formatted(email),
                201).get("id").asLong();
        JsonNode login = postAnonymous("/api/auth/login", "{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email), 200);
        staff = new Tenant(login.at("/user/tenantId").asLong(), "Bearer " + login.get("token").asText());

        facebook = get(owner, "/api/platforms", 200).get(0).get("id").asLong();
        customer = post(owner, "/api/customers", "{\"name\":\"Buyer\"}", 201).get("id").asLong();
        rice = post(owner, "/api/products", "{\"name\":\"Rice\",\"costPrice\":100,\"sellingPrice\":150}", 201).get("id").asLong();
    }

    private String orderBody(String status, int quantity, String deliveryCharge, String notes) {
        return """
                {"platformId":%d,"customerId":%d,"status":"%s","deliveryCharge":%s,"orderedAt":"2026-05-01T10:00:00",
                 "notes":%s,"items":[{"productId":%d,"quantity":%d,"soldPrice":150}]}
                """.formatted(facebook, customer, status, deliveryCharge, notes == null ? "null" : "\"" + notes + "\"",
                rice, quantity);
    }

    /** Newest first. */
    private JsonNode log(String query) throws Exception {
        return get(owner, "/api/activity" + query, 200).get("content");
    }

    private static List<String> actions(JsonNode rows) {
        List<String> result = new ArrayList<>();
        rows.forEach(r -> result.add(r.get("action").asText()));
        return result;
    }

    private static void assertMoney(String expected, JsonNode actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual.decimalValue()), "expected " + expected + " but was " + actual);
    }

    @Test
    void everyChangeToAnOrderIsLoggedWithWhoMadeIt() throws Exception {
        long id = post(staff, "/api/orders", orderBody("PENDING", 2, "60", null), 201).get("id").asLong();

        // Saving without changing anything is not an edit.
        put(owner, "/api/orders/" + id, orderBody("PENDING", 2, "60.00", null), 200);
        // An edit lists what it changed and how the total moved: 2 x 150 + 60 = 360 becomes 3 x 150 + 60 = 510.
        put(owner, "/api/orders/" + id, orderBody("PAID", 3, "60", "fragile"), 200);
        patch(staff, "/api/orders/" + id + "/status", "{\"status\":\"RETURNED\"}", 200);
        patch(staff, "/api/orders/" + id + "/status", "{\"status\":\"RETURNED\"}", 200); // no change, no entry
        delete(staff, "/api/orders/" + id, 204);

        JsonNode rows = log("?orderId=" + id);
        assertEquals(List.of("DELETED", "STATUS_CHANGED", "EDITED", "CREATED"), actions(rows));

        JsonNode created = rows.get(3);
        assertEquals("Rina", created.get("userName").asText());
        assertEquals("STAFF", created.get("userRole").asText());
        assertEquals("Buyer", created.get("customerName").asText());
        assertEquals("PENDING", created.get("toStatus").asText());
        assertMoney("360", created.get("amount"));

        JsonNode edited = rows.get(2);
        assertEquals("Owner", edited.get("userName").asText());
        assertEquals("[\"STATUS\",\"ITEMS\",\"NOTES\"]", edited.get("changes").toString());
        assertEquals("PENDING", edited.get("fromStatus").asText());
        assertEquals("PAID", edited.get("toStatus").asText());
        assertMoney("360", edited.get("amountBefore"));
        assertMoney("510", edited.get("amount"));

        JsonNode status = rows.get(1);
        assertEquals("PAID", status.get("fromStatus").asText());
        assertEquals("RETURNED", status.get("toStatus").asText());

        // The deleted order keeps its history, marked as gone.
        JsonNode deleted = rows.get(0);
        assertEquals("Rina", deleted.get("userName").asText());
        assertMoney("510", deleted.get("amount"));
        rows.forEach(r -> assertFalse(r.get("orderExists").asBoolean()));
    }

    @Test
    void courierPayoutsAreLoggedAndTheLogCanBeFiltered() throws Exception {
        long pathao = -1;
        for (JsonNode c : get(owner, "/api/couriers", 200)) {
            if (c.get("name").asText().equals("Pathao")) pathao = c.get("id").asLong();
        }
        long id = post(owner, "/api/orders", """
                {"platformId":%d,"customerId":%d,"status":"PENDING","courierId":%d,"codAmount":300,
                 "items":[{"productId":%d,"quantity":2,"soldPrice":150}]}
                """.formatted(facebook, customer, pathao, rice), 201).get("id").asLong();
        post(staff, "/api/orders", orderBody("PAID", 1, "0", null), 201);

        post(owner, "/api/couriers/payouts", "{\"orderIds\":[%d]}".formatted(id), 204);
        post(owner, "/api/couriers/payouts/undo", "{\"orderIds\":[%d]}".formatted(id), 204);
        post(owner, "/api/couriers/payouts/undo", "{\"orderIds\":[%d]}".formatted(id), 204); // nothing left to undo

        // A payout on a pending order also marks it delivered, which is its own entry.
        assertEquals(List.of("PAYOUT_UNDONE", "PAID_OUT", "STATUS_CHANGED", "CREATED"), actions(log("?orderId=" + id)));
        assertMoney("300", log("?orderId=" + id + "&action=PAID_OUT").get(0).get("amount"));
        assertTrue(log("?orderId=" + id).get(0).get("orderExists").asBoolean());

        JsonNode byStaff = log("?userId=" + staffId);
        assertEquals(List.of("CREATED"), actions(byStaff));
        assertEquals(0, log("?from=2020-01-01&to=2020-12-31").size());
    }

    @Test
    void onlyOwnerAndAdminReadTheLogAndEachBusinessSeesItsOwn() throws Exception {
        post(owner, "/api/orders", orderBody("PAID", 1, "0", null), 201);
        get(staff, "/api/activity", 403);

        Tenant other = registerTenant();
        assertEquals(0, get(other, "/api/activity", 200).get("totalElements").asLong());
        assertEquals(1, get(owner, "/api/activity", 200).get("totalElements").asLong());
    }
}
