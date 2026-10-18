package com.salestracker.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.salestracker.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** The AI is stubbed; everything else (customers, products, platforms, orders, stock) is the real app on a real database. */
class DraftIntegrationTest extends AbstractIntegrationTest {

    @MockBean AiClient ai;

    private Tenant tenant;
    private long shirt;
    private long redShirt;

    @BeforeEach
    void setUp() throws Exception {
        tenant = registerTenant();
        shirt = post(tenant, "/api/products", "{\"name\":\"Blue Shirt\",\"costPrice\":500,\"sellingPrice\":900,\"stockQty\":10}", 201).get("id").asLong();
        redShirt = post(tenant, "/api/products", "{\"name\":\"Red Shirt\",\"costPrice\":500,\"sellingPrice\":900,\"stockQty\":1}", 201).get("id").asLong();
    }

    private static ExtractedOrder.Item item(String name, Integer qty, String price) {
        return new ExtractedOrder.Item(name, qty, price == null ? null : new BigDecimal(price));
    }

    private JsonNode draft(ExtractedOrder x) throws Exception {
        when(ai.extractOrder(anyString())).thenReturn(x);
        return post(tenant, "/api/agent/draft", "{\"text\":\"order\"}", 200);
    }

    private static boolean hasIssue(JsonNode draft, String level, String part) {
        for (JsonNode i : draft.get("issues")) {
            if (i.get("level").asText().equals(level) && i.get("message").asText().contains(part)) return true;
        }
        return false;
    }

    @Test
    void aCompleteMessageBecomesAConfirmableDraftForANewCustomer() throws Exception {
        JsonNode d = draft(new ExtractedOrder("Rahim", "01711222333", "Mirpur 10, Dhaka", "Facebook", new BigDecimal("60"),
                "call first", List.of(item("blue shirts", 2, "900"))));

        assertTrue(d.get("canConfirm").asBoolean());
        assertTrue(d.at("/customer/isNew").asBoolean());
        assertEquals("Rahim", d.at("/customer/name").asText());
        assertEquals("Facebook Page", d.get("platformName").asText());
        assertEquals("PENDING", d.get("status").asText());
        assertEquals(60, d.get("deliveryCharge").asInt());
        assertEquals(shirt, d.at("/items/0/productId").asLong());
        assertEquals("MATCHED", d.at("/items/0/match").asText());
        assertEquals(2, d.at("/items/0/quantity").asInt());
        assertEquals(0, d.get("issues").size());

        // Nothing is saved by drafting.
        assertEquals(0, get(tenant, "/api/customers", 200).get("content").size());
        assertEquals(0, get(tenant, "/api/orders", 200).get("content").size());
    }

    @Test
    void confirmingCreatesTheCustomerAndTheOrderThenTheNextOrderFindsThem() throws Exception {
        JsonNode d = draft(new ExtractedOrder("Rahim", "01711-222333", "Mirpur 10", null, null, null,
                List.of(item("Blue Shirt", 2, "900"))));
        String body = """
                {"platformId":%d,"newCustomer":{"name":"Rahim","phone":"01711-222333","address":"Mirpur 10"},
                 "status":"PENDING","items":[{"productId":%d,"quantity":2,"soldPrice":900}]}
                """.formatted(d.get("platformId").asLong(), shirt);
        JsonNode order = post(tenant, "/api/agent/confirm", body, 201);
        assertEquals("Rahim", order.get("customerName").asText());
        assertEquals(1800, order.get("revenue").asInt());
        assertEquals(8, stock(shirt)); // the order took its 2 units, as with any order

        // Same person again, number typed another way: found, not duplicated.
        JsonNode again = draft(new ExtractedOrder("Rahim", "+8801711222333", null, null, null, null,
                List.of(item("Blue Shirt", 1, "900"))));
        assertFalse(again.at("/customer/isNew").asBoolean());
        assertEquals(order.get("customerId").asLong(), again.at("/customer/id").asLong());
        assertEquals("Mirpur 10", again.at("/customer/address").asText());

        // Even if a stale draft still asks for a new customer, confirm reuses the one on file.
        String stale = """
                {"platformId":%d,"newCustomer":{"name":"Rahim","phone":"+880 1711 222333"},
                 "status":"PENDING","items":[{"productId":%d,"quantity":1,"soldPrice":900}]}
                """.formatted(d.get("platformId").asLong(), shirt);
        JsonNode second = post(tenant, "/api/agent/confirm", stale, 201);
        assertEquals(order.get("customerId").asLong(), second.get("customerId").asLong());
        assertEquals(1, get(tenant, "/api/customers", 200).get("content").size());
    }

    private int stock(long productId) throws Exception {
        for (JsonNode p : get(tenant, "/api/products", 200).get("content")) {
            if (p.get("id").asLong() == productId) return p.get("stockQty").asInt();
        }
        throw new AssertionError("no product " + productId);
    }

    @Test
    void problemsBlockConfirmingAndAreListed() throws Exception {
        JsonNode d = draft(new ExtractedOrder(null, null, null, null, null, null,
                List.of(item("shirt", 1, null), item("laptop", 1, "5000"))));

        assertFalse(d.get("canConfirm").asBoolean());
        assertTrue(hasIssue(d, "ERROR", "name is missing"));
        assertTrue(hasIssue(d, "ERROR", "more than one product"));
        assertTrue(hasIssue(d, "ERROR", "No product matches \"laptop\""));
        assertTrue(hasIssue(d, "WARNING", "No phone number"));
        assertEquals("AMBIGUOUS", d.at("/items/0/match").asText());
        assertEquals(2, d.at("/items/0/candidates").size());
        assertEquals("NOT_FOUND", d.at("/items/1/match").asText());
        assertTrue(d.at("/items/1/productId").isNull());
    }

    @Test
    void noProductsAtAllIsAnError() throws Exception {
        JsonNode d = draft(new ExtractedOrder("Karim", "01811111111", "Dhaka", null, null, null, List.of()));
        assertFalse(d.get("canConfirm").asBoolean());
        assertTrue(hasIssue(d, "ERROR", "No products"));
    }

    @Test
    void priceAndStockDifferencesAreWarningsOnly() throws Exception {
        JsonNode d = draft(new ExtractedOrder("Karim", "01811111111", "Dhaka", "facebook", null, null,
                List.of(item("Red Shirt", 3, "800"), item("Blue Shirt", null, null))));

        assertTrue(d.get("canConfirm").asBoolean());
        assertTrue(hasIssue(d, "WARNING", "800 in the message but 900"));
        assertTrue(hasIssue(d, "WARNING", "Only 1 of Red Shirt in stock"));
        assertTrue(hasIssue(d, "WARNING", "No price for Blue Shirt"));
        assertEquals(redShirt, d.at("/items/0/productId").asLong());
        assertEquals(800, d.at("/items/0/soldPrice").asInt());
        assertEquals(900, d.at("/items/1/soldPrice").asInt());
        assertEquals(1, d.at("/items/1/quantity").asInt());
    }

    @Test
    void anUnknownPlatformFallsBackToFacebookAndCustomersStayPerShop() throws Exception {
        JsonNode d = draft(new ExtractedOrder("Karim", "01811111111", "Dhaka", "Instagram DM", null, null,
                List.of(item("Blue Shirt", 1, "900"))));
        assertEquals("Facebook Page", d.get("platformName").asText());

        // Another shop's customer with the same number is not this shop's customer.
        Tenant other = registerTenant();
        post(other, "/api/customers", "{\"name\":\"Someone Else\",\"phone\":\"01811111111\"}", 201);
        JsonNode again = draft(new ExtractedOrder("Karim", "01811111111", "Dhaka", null, null, null,
                List.of(item("Blue Shirt", 1, "900"))));
        assertTrue(again.at("/customer/isNew").asBoolean());
    }

    @Test
    void aKnownPhoneWithADifferentNameIsFlagged() throws Exception {
        post(tenant, "/api/customers", "{\"name\":\"Rahim Uddin\",\"phone\":\"01711222333\"}", 201);
        JsonNode d = draft(new ExtractedOrder("Karim", "01711222333", null, null, null, null,
                List.of(item("Blue Shirt", 1, "900"))));
        assertFalse(d.at("/customer/isNew").asBoolean());
        assertEquals("Rahim Uddin", d.at("/customer/name").asText());
        assertTrue(hasIssue(d, "WARNING", "belongs to Rahim Uddin"));
        assertTrue(d.get("canConfirm").asBoolean());
    }
}
