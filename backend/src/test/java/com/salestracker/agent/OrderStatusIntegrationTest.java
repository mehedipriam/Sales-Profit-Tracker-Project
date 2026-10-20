package com.salestracker.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.salestracker.AbstractIntegrationTest;
import com.salestracker.agent.Intent.Kind;
import com.salestracker.order.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** Changing an order's status from the chat. The AI is stubbed to name the order and the new status. */
class OrderStatusIntegrationTest extends AbstractIntegrationTest {

    @MockBean AiClient ai;

    private Tenant owner;
    private long facebook;
    private long imdad;
    private long rice;

    @BeforeEach
    void setUp() throws Exception {
        owner = registerTenant();
        for (JsonNode p : get(owner, "/api/platforms", 200)) {
            if (p.get("name").asText().equals("Facebook Page")) facebook = p.get("id").asLong();
        }
        imdad = post(owner, "/api/customers", "{\"name\":\"Imdad Hossain\",\"phone\":\"01711111111\"}", 201).get("id").asLong();
        rice = post(owner, "/api/products", "{\"name\":\"Rice\",\"costPrice\":100,\"sellingPrice\":150,\"stockQty\":10}", 201).get("id").asLong();
    }

    // ---- helpers ----

    private JsonNode order(long customerId, String status, int qty, String orderedAt) throws Exception {
        return post(owner, "/api/orders", ("{\"platformId\":%d,\"customerId\":%d,\"status\":\"%s\",\"deliveryCharge\":50,"
                + "\"orderedAt\":\"%s\",\"items\":[{\"productId\":%d,\"quantity\":%d,\"soldPrice\":150}]}")
                .formatted(facebook, customerId, status, orderedAt, rice, qty), 201);
    }

    private static Intent status(String ref, String customer, OrderStatus to) {
        return new Intent(Kind.ORDER_STATUS, null, null, null, null, customer, null, null, null, null, null, ref, to);
    }

    private JsonNode say(Tenant who, Intent intent) throws Exception {
        when(ai.understand(anyString(), any())).thenReturn(intent);
        return post(who, "/api/agent/message", "{\"text\":\"a message\"}", 200);
    }

    private int stock() throws Exception {
        for (JsonNode p : get(owner, "/api/products", 200).get("content")) if (p.get("id").asLong() == rice) return p.get("stockQty").asInt();
        throw new AssertionError("no product");
    }

    private String statusOf(long orderId) throws Exception {
        return get(owner, "/api/orders/" + orderId, 200).get("status").asText();
    }

    private String now() {
        return java.time.LocalDateTime.now().withNano(0).toString();
    }

    private Tenant staff() throws Exception {
        String email = "staff-" + System.nanoTime() + "@example.com";
        post(owner, "/api/users", "{\"fullName\":\"Rina\",\"email\":\"%s\",\"password\":\"password123\"}".formatted(email), 201);
        JsonNode login = postAnonymous("/api/auth/login", "{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email), 200);
        return new Tenant(owner.id(), "Bearer " + login.get("token").asText());
    }

    // ---- the card ----

    @Test
    void theCardShowsTheOrderTheChangeAndItsEffectAndSavesNothing() throws Exception {
        JsonNode o = order(imdad, "PENDING", 2, now());
        JsonNode r = say(owner, status(o.get("orderNo").asText(), null, OrderStatus.RETURNED));
        assertEquals("STATUS", r.get("type").asText());
        JsonNode c = r.get("statusChange");
        assertEquals(o.get("id").asLong(), c.get("orderId").asLong());
        assertEquals(o.get("orderNo").asText(), c.get("orderNo").asText());
        assertEquals("Imdad Hossain", c.get("customerName").asText());
        assertEquals("Rice x 2", c.get("items").get(0).asText());
        assertEquals(350, c.get("total").asInt()); // 2 x 150 + 50 delivery
        assertEquals("PENDING", c.get("from").asText());
        assertEquals("RETURNED", c.get("to").asText());
        assertTrue(c.get("effect").asText().contains("stock is put back"));
        assertEquals("PENDING", statusOf(o.get("id").asLong()));
        assertEquals(8, stock());
    }

    @Test
    void anOrderIsFoundByItsNumberItsCountOrItsCustomersLastOrder() throws Exception {
        JsonNode earlier = order(imdad, "PENDING", 1, java.time.LocalDateTime.now().minusHours(2).withNano(0).toString());
        JsonNode latest = order(imdad, "PENDING", 1, now());
        String latestNo = latest.get("orderNo").asText();
        String count = String.valueOf(Integer.parseInt(latestNo.substring(latestNo.lastIndexOf('-') + 1)));

        // The full number, however it is written.
        assertEquals(earlier.get("id").asLong(), say(owner, status(earlier.get("orderNo").asText().toLowerCase(), null, OrderStatus.PAID))
                .at("/statusChange/orderId").asLong());
        assertEquals(earlier.get("id").asLong(), say(owner, status(earlier.get("orderNo").asText().replace("-", ""), null, OrderStatus.PAID))
                .at("/statusChange/orderId").asLong());
        // Just its count in the current year: "order 2" and "#2" are the second order of the year.
        assertEquals(latest.get("id").asLong(), say(owner, status(count, null, OrderStatus.PAID)).at("/statusChange/orderId").asLong());
        assertEquals(latest.get("id").asLong(), say(owner, status("#" + count, null, OrderStatus.PAID)).at("/statusChange/orderId").asLong());
        // "The last order of Imdad" is the newest by date.
        assertEquals(latest.get("id").asLong(), say(owner, status(null, "Imdad", OrderStatus.CANCELLED)).at("/statusChange/orderId").asLong());
    }

    @Test
    void sameStatusUnknownOrdersAndMissingDetailsGivePlainMessages() throws Exception {
        JsonNode o = order(imdad, "PAID", 1, now());
        JsonNode same = say(owner, status(o.get("orderNo").asText(), null, OrderStatus.PAID));
        assertEquals("MESSAGE", same.get("type").asText());
        assertTrue(same.get("message").asText().contains("Nothing to do"));

        assertTrue(say(owner, status("ORD-2601-9999", null, OrderStatus.PAID)).get("message").asText().contains("No order matches"));
        assertTrue(say(owner, status("whatever", null, OrderStatus.PAID)).get("message").asText().contains("No order matches"));
        assertEquals("MESSAGE", say(owner, status(null, null, OrderStatus.PAID)).get("type").asText());
        assertEquals("MESSAGE", say(owner, status(o.get("orderNo").asText(), null, null)).get("type").asText());

        post(owner, "/api/customers", "{\"name\":\"Imdad Khan\"}", 201);
        assertTrue(say(owner, status(null, "Imdad", OrderStatus.PAID)).get("message").asText().contains("Which Imdad"));
        assertTrue(say(owner, status(null, "Zubair", OrderStatus.PAID)).get("message").asText().contains("No customer"));
        long lonely = post(owner, "/api/customers", "{\"name\":\"Lonely\"}", 201).get("id").asLong();
        assertTrue(say(owner, status(null, "Lonely", OrderStatus.PAID)).get("message").asText().contains("no orders"));
        assertTrue(lonely > 0);
    }

    @Test
    void anotherShopsOrderIsNeverFoundOrChanged() throws Exception {
        Tenant other = registerTenant();
        long theirCustomer = post(other, "/api/customers", "{\"name\":\"Zubair\"}", 201).get("id").asLong();
        long theirProduct = post(other, "/api/products", "{\"name\":\"Soap\",\"costPrice\":10,\"sellingPrice\":20}", 201).get("id").asLong();
        long theirPlatform = get(other, "/api/platforms", 200).get(0).get("id").asLong();
        JsonNode theirs = post(other, "/api/orders", ("{\"platformId\":%d,\"customerId\":%d,\"status\":\"PENDING\","
                + "\"items\":[{\"productId\":%d,\"quantity\":1,\"soldPrice\":20}]}").formatted(theirPlatform, theirCustomer, theirProduct), 201);

        assertTrue(say(owner, status(theirs.get("orderNo").asText(), null, OrderStatus.PAID)).get("message").asText().contains("No order matches"));
        assertTrue(say(owner, status(null, "Zubair", OrderStatus.PAID)).get("message").asText().contains("No customer"));
        post(owner, "/api/agent/orders/" + theirs.get("id").asLong() + "/status", "{\"from\":\"PENDING\",\"to\":\"PAID\"}", 404);
        assertEquals("PENDING", get(other, "/api/orders/" + theirs.get("id").asLong(), 200).get("status").asText());
    }

    // ---- confirm ----

    @Test
    void returningAnOrderPutsTheStockBackRemovesItFromProfitAndIsLoggedAsTheAssistant() throws Exception {
        JsonNode o = order(imdad, "PAID", 2, now());
        long id = o.get("id").asLong();
        assertEquals(8, stock());
        assertEquals(1, get(owner, "/api/reports/summary", 200).at("/realized/orders").asInt());

        JsonNode changed = post(owner, "/api/agent/orders/" + id + "/status", "{\"from\":\"PAID\",\"to\":\"RETURNED\"}", 200);
        assertEquals("RETURNED", changed.get("status").asText());
        assertEquals(10, stock());
        assertEquals(0, get(owner, "/api/reports/summary", 200).at("/realized/orders").asInt());

        JsonNode log = get(owner, "/api/activity?orderId=" + id + "&action=STATUS_CHANGED", 200).get("content");
        assertEquals(1, log.size());
        assertTrue(log.get(0).get("viaAgent").asBoolean());
        assertEquals("PAID", log.get(0).get("fromStatus").asText());
        assertEquals("RETURNED", log.get(0).get("toStatus").asText());

        // And back to paid: the stock is taken out again.
        post(owner, "/api/agent/orders/" + id + "/status", "{\"from\":\"RETURNED\",\"to\":\"PAID\"}", 200);
        assertEquals(8, stock());
        assertEquals(1, get(owner, "/api/reports/summary", 200).at("/realized/orders").asInt());
    }

    @Test
    void cancellingPutsTheStockBackToo() throws Exception {
        long id = order(imdad, "PENDING", 3, now()).get("id").asLong();
        assertEquals(7, stock());
        post(owner, "/api/agent/orders/" + id + "/status", "{\"from\":\"PENDING\",\"to\":\"CANCELLED\"}", 200);
        assertEquals(10, stock());
        assertEquals("CANCELLED", statusOf(id));
    }

    @Test
    void aStaleCardIsRefusedAndNothingChanges() throws Exception {
        long id = order(imdad, "PENDING", 1, now()).get("id").asLong();
        post(owner, "/api/agent/orders/" + id + "/status", "{\"from\":\"PENDING\",\"to\":\"PAID\"}", 200);
        String message = post(owner, "/api/agent/orders/" + id + "/status", "{\"from\":\"PENDING\",\"to\":\"RETURNED\"}", 409).get("message").asText();
        assertTrue(message.contains("is now paid, not pending"));
        assertEquals("PAID", statusOf(id));
        post(owner, "/api/agent/orders/" + id + "/status", "{\"from\":\"PAID\",\"to\":\"PAID\"}", 400);
        post(owner, "/api/agent/orders/" + id + "/status", "{\"from\":\"PAID\"}", 400);
    }

    @Test
    void staffCanChangeStatusAndDoNotSeeCostOrProfit() throws Exception {
        long id = order(imdad, "PENDING", 1, now()).get("id").asLong();
        Tenant staff = staff();
        assertEquals("STATUS", say(staff, status(null, "Imdad", OrderStatus.PAID)).get("type").asText());
        JsonNode changed = post(staff, "/api/agent/orders/" + id + "/status", "{\"from\":\"PENDING\",\"to\":\"PAID\"}", 200);
        assertEquals("PAID", changed.get("status").asText());
        assertTrue(changed.get("cost").isNull());
        assertTrue(changed.get("profit").isNull());
    }

    @Test
    void theStatusEndpointNeedsALogin() throws Exception {
        assertEquals(401, mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/agent/orders/1/status")
                .contentType("application/json").content("{}")).andReturn().getResponse().getStatus());
    }

    @Test
    void effectsAreDescribedForEveryKindOfMove() {
        assertTrue(OrderStatusAssistant.effect(OrderStatus.PAID, OrderStatus.RETURNED).contains("put back"));
        assertTrue(OrderStatusAssistant.effect(OrderStatus.RETURNED, OrderStatus.CANCELLED).contains("stays back"));
        assertTrue(OrderStatusAssistant.effect(OrderStatus.PENDING, OrderStatus.PAID).contains("counts as revenue"));
        assertTrue(OrderStatusAssistant.effect(OrderStatus.CANCELLED, OrderStatus.PAID).contains("taken out again"));
        assertTrue(OrderStatusAssistant.effect(OrderStatus.PAID, OrderStatus.PENDING).contains("until it is paid"));
    }
}
