package com.salestracker.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.salestracker.AbstractIntegrationTest;
import com.salestracker.agent.Intent.Kind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** Adding, finding and updating customers from the chat. The AI is stubbed to choose the action and details. */
class CustomerAssistIntegrationTest extends AbstractIntegrationTest {

    @MockBean AiClient ai;

    private Tenant owner;
    private long facebook;
    private long imdad;

    @BeforeEach
    void setUp() throws Exception {
        owner = registerTenant();
        for (JsonNode p : get(owner, "/api/platforms", 200)) {
            if (p.get("name").asText().equals("Facebook Page")) facebook = p.get("id").asLong();
        }
        imdad = post(owner, "/api/customers", ("{\"name\":\"Imdad Hossain\",\"phone\":\"01711111111\","
                + "\"address\":\"Old Road 1\",\"sourcePlatformId\":%d,\"notes\":\"VIP\"}").formatted(facebook), 201).get("id").asLong();
    }

    // ---- helpers ----

    private static Intent customerIntent(Kind kind, String name, String phone, String address, String platform, String newName) {
        return new Intent(kind, null, null, null, null, name, null, phone, address, platform, newName);
    }

    private JsonNode say(Tenant who, Intent intent) throws Exception {
        when(ai.understand(anyString(), any())).thenReturn(intent);
        return post(who, "/api/agent/message", "{\"text\":\"a message\"}", 200);
    }

    private JsonNode customer(long id) throws Exception {
        for (JsonNode c : get(owner, "/api/customers?size=50", 200).get("content")) if (c.get("id").asLong() == id) return c;
        throw new AssertionError("no customer " + id);
    }

    private Tenant staff() throws Exception {
        String email = "staff-" + System.nanoTime() + "@example.com";
        post(owner, "/api/users", "{\"fullName\":\"Rina\",\"email\":\"%s\",\"password\":\"password123\"}".formatted(email), 201);
        JsonNode login = postAnonymous("/api/auth/login", "{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email), 200);
        return new Tenant(owner.id(), "Bearer " + login.get("token").asText());
    }

    private static JsonNode field(JsonNode proposal, String key) {
        for (JsonNode f : proposal.get("fields")) if (f.get("key").asText().equals(key)) return f;
        return null;
    }

    // ---- add ----

    @Test
    void addingShowsACardWithTheGivenDetailsAndSavesNothing() throws Exception {
        JsonNode r = say(owner, customerIntent(Kind.CUSTOMER_ADD, "Rahim", "01733333333", "Mirpur 10", "facebook", null));
        assertEquals("PROPOSAL", r.get("type").asText());
        JsonNode p = r.get("proposal");
        assertEquals("ADD", p.get("kind").asText());
        assertEquals("Rahim", field(p, "name").get("after").asText());
        assertEquals("01733333333", field(p, "phone").get("after").asText());
        assertEquals("Mirpur 10", field(p, "address").get("after").asText());
        assertEquals("Facebook Page", field(p, "platform").get("after").asText());
        assertEquals(facebook, p.get("platformId").asLong());
        assertEquals(1, get(owner, "/api/customers", 200).get("content").size()); // still only Imdad
    }

    @Test
    void anUnknownPlatformIsLeftBlankWithANote() throws Exception {
        JsonNode p = say(owner, customerIntent(Kind.CUSTOMER_ADD, "Rahim", null, null, "Instagram", null)).get("proposal");
        assertEquals("", field(p, "platform").get("after").asText());
        assertTrue(p.get("platformId").isNull());
        assertTrue(p.get("warnings").get(0).asText().contains("Instagram"));
    }

    @Test
    void askingToAddWithNoDetailsGivesABlankCardToFillIn() throws Exception {
        JsonNode r = say(owner, customerIntent(Kind.CUSTOMER_ADD, null, null, null, null, null));
        assertEquals("PROPOSAL", r.get("type").asText());
        assertEquals("", field(r.get("proposal"), "name").get("after").asText());
    }

    @Test
    void aPhoneAlreadyOnFileIsReportedNotDuplicated() throws Exception {
        JsonNode r = say(owner, customerIntent(Kind.CUSTOMER_ADD, "Someone", "+880 1711-111111", null, null, null));
        assertEquals("MESSAGE", r.get("type").asText());
        assertTrue(r.get("message").asText().contains("already saved as Imdad Hossain"));
    }

    @Test
    void confirmingCreatesTheCustomerAndRefusesADuplicatePhone() throws Exception {
        JsonNode made = post(owner, "/api/agent/customers", ("{\"name\":\"Rahim\",\"phone\":\"01733333333\","
                + "\"address\":\"Mirpur 10\",\"sourcePlatformId\":%d}").formatted(facebook), 201);
        assertEquals("Rahim", made.get("name").asText());
        assertEquals(2, get(owner, "/api/customers", 200).get("content").size());

        String dup = post(owner, "/api/agent/customers", "{\"name\":\"Rahim 2\",\"phone\":\"0173-333-3333\"}", 409).get("message").asText();
        assertTrue(dup.contains("already saved as Rahim"));
        assertEquals(2, get(owner, "/api/customers", 200).get("content").size());

        post(owner, "/api/agent/customers", "{\"name\":\"  \"}", 400);
        post(owner, "/api/agent/customers", "{\"name\":\"X\",\"sourcePlatformId\":999999}", 400);
    }

    // ---- find ----

    @Test
    void findShowsPhoneAddressOrdersAndLastOrder() throws Exception {
        long product = post(owner, "/api/products", "{\"name\":\"Rice\",\"costPrice\":100,\"sellingPrice\":150}", 201).get("id").asLong();
        for (String at : new String[]{"2026-09-05T12:00:00", "2026-09-20T12:00:00"}) {
            post(owner, "/api/orders", ("{\"platformId\":%d,\"customerId\":%d,\"status\":\"PAID\",\"orderedAt\":\"%s\","
                    + "\"deliveryCharge\":50,\"items\":[{\"productId\":%d,\"quantity\":2,\"soldPrice\":150}]}")
                    .formatted(facebook, imdad, at, product), 201);
        }
        JsonNode r = say(owner, customerIntent(Kind.CUSTOMER_FIND, "imdad", null, null, null, null));
        assertEquals("CUSTOMER", r.get("type").asText());
        JsonNode c = r.get("customer");
        assertEquals("Imdad Hossain", c.get("name").asText());
        assertEquals("01711111111", c.get("phone").asText());
        assertEquals("Old Road 1", c.get("address").asText());
        assertEquals("Facebook Page", c.get("platform").asText());
        assertEquals(2, c.get("orders").asInt());
        assertTrue(c.get("lastOrderAt").asText().startsWith("2026-09-20"));
        assertEquals(700, c.get("spent").asInt()); // 2 x (300 + 50)
    }

    @Test
    void findByPhoneAndTheNameProblems() throws Exception {
        post(owner, "/api/customers", "{\"name\":\"Imdad Khan\",\"phone\":\"01722222222\"}", 201);

        assertEquals("Imdad Hossain", say(owner, customerIntent(Kind.CUSTOMER_FIND, null, "+8801711111111", null, null, null))
                .at("/customer/name").asText());
        assertEquals("Imdad Khan", say(owner, customerIntent(Kind.CUSTOMER_FIND, "imdad khan", null, null, null, null))
                .at("/customer/name").asText());

        JsonNode several = say(owner, customerIntent(Kind.CUSTOMER_FIND, "Imdad", null, null, null, null));
        assertEquals("MESSAGE", several.get("type").asText());
        assertTrue(several.get("message").asText().contains("Imdad Hossain"));
        assertTrue(several.get("message").asText().contains("Imdad Khan"));

        assertTrue(say(owner, customerIntent(Kind.CUSTOMER_FIND, "Zubair", null, null, null, null)).get("message").asText().contains("No customer"));
        assertEquals("MESSAGE", say(owner, customerIntent(Kind.CUSTOMER_FIND, null, null, null, null, null)).get("type").asText());
    }

    @Test
    void staffCanFindACustomerButDoesNotSeeWhatTheySpent() throws Exception {
        JsonNode c = say(staff(), customerIntent(Kind.CUSTOMER_FIND, "Imdad", null, null, null, null)).get("customer");
        assertEquals("Old Road 1", c.get("address").asText());
        assertTrue(c.get("spent").isNull());
        // and can add one
        assertEquals("PROPOSAL", say(staff(), customerIntent(Kind.CUSTOMER_ADD, "Rahim", null, null, null, null)).get("type").asText());
    }

    @Test
    void anotherShopsCustomersAreNeverFoundOrChanged() throws Exception {
        Tenant other = registerTenant();
        post(other, "/api/customers", "{\"name\":\"Zubair\",\"phone\":\"01799999999\"}", 201);
        assertTrue(say(owner, customerIntent(Kind.CUSTOMER_FIND, "Zubair", null, null, null, null)).get("message").asText().contains("No customer"));
        assertTrue(say(owner, customerIntent(Kind.CUSTOMER_FIND, null, "01799999999", null, null, null)).get("message").asText().contains("No customer"));
        // The same number is free to be added here.
        assertEquals("PROPOSAL", say(owner, customerIntent(Kind.CUSTOMER_ADD, "Z", "01799999999", null, null, null)).get("type").asText());
        // And someone else's customer id cannot be patched through this shop.
        long theirs = get(other, "/api/customers", 200).get("content").get(0).get("id").asLong();
        patch(owner, "/api/agent/customers/" + theirs, "{\"address\":\"hacked\"}", 404);
    }

    // ---- update ----

    @Test
    void anUpdateShowsBeforeAndAfterForTheChangedFieldsOnly() throws Exception {
        JsonNode p = say(owner, customerIntent(Kind.CUSTOMER_UPDATE, "Imdad", null, "House 12, Road 3, Dhanmondi", null, null)).get("proposal");
        assertEquals("UPDATE", p.get("kind").asText());
        assertEquals(imdad, p.get("customerId").asLong());
        assertEquals("Imdad Hossain", p.get("customerName").asText());
        assertEquals(1, p.get("fields").size());
        assertEquals("Old Road 1", field(p, "address").get("before").asText());
        assertEquals("House 12, Road 3, Dhanmondi", field(p, "address").get("after").asText());
        assertEquals("Old Road 1", customer(imdad).get("address").asText()); // nothing saved yet
    }

    @Test
    void valuesThatAreAlreadySavedAreNotListed() throws Exception {
        JsonNode same = say(owner, customerIntent(Kind.CUSTOMER_UPDATE, "Imdad", "01711111111", "Old Road 1", "Facebook", null));
        assertEquals("MESSAGE", same.get("type").asText());
        assertTrue(same.get("message").asText().contains("Nothing to change"));

        JsonNode p = say(owner, customerIntent(Kind.CUSTOMER_UPDATE, "Imdad", "01711111111", "New Road", null, null)).get("proposal");
        assertEquals(1, p.get("fields").size());
        assertNull(field(p, "phone"));
    }

    @Test
    void aPhoneBelongingToAnotherCustomerBlocksTheUpdate() throws Exception {
        post(owner, "/api/customers", "{\"name\":\"Rahim\",\"phone\":\"01733333333\"}", 201);
        JsonNode r = say(owner, customerIntent(Kind.CUSTOMER_UPDATE, "Imdad", "01733333333", null, null, null));
        assertEquals("MESSAGE", r.get("type").asText());
        assertTrue(r.get("message").asText().contains("already saved for Rahim"));
        patch(owner, "/api/agent/customers/" + imdad, "{\"phone\":\"0173-333-3333\"}", 409);
    }

    @Test
    void updateNeedsWhoAndWhatAndAUniqueMatch() throws Exception {
        assertEquals("MESSAGE", say(owner, customerIntent(Kind.CUSTOMER_UPDATE, null, null, "X", null, null)).get("type").asText());
        assertEquals("MESSAGE", say(owner, customerIntent(Kind.CUSTOMER_UPDATE, "Imdad", null, null, null, null)).get("type").asText());
        assertTrue(say(owner, customerIntent(Kind.CUSTOMER_UPDATE, "Zubair", null, "X", null, null)).get("message").asText().contains("No customer"));
        post(owner, "/api/customers", "{\"name\":\"Imdad Khan\"}", 201);
        assertTrue(say(owner, customerIntent(Kind.CUSTOMER_UPDATE, "Imdad", null, "X", null, null)).get("message").asText().contains("Which Imdad"));
    }

    @Test
    void confirmingChangesOnlyTheStatedFields() throws Exception {
        JsonNode saved = patch(owner, "/api/agent/customers/" + imdad, "{\"address\":\"House 12, Dhanmondi\"}", 200);
        assertEquals("House 12, Dhanmondi", saved.get("address").asText());
        JsonNode c = customer(imdad);
        assertEquals("Imdad Hossain", c.get("name").asText());
        assertEquals("01711111111", c.get("phone").asText());
        assertEquals(facebook, c.get("sourcePlatformId").asLong());
        assertEquals("VIP", c.get("notes").asText());

        patch(owner, "/api/agent/customers/" + imdad, "{\"name\":\"Imdad H.\"}", 200);
        assertEquals("House 12, Dhanmondi", customer(imdad).get("address").asText());
        assertEquals("Imdad H.", customer(imdad).get("name").asText());
        patch(owner, "/api/agent/customers/" + imdad, "{\"sourcePlatformId\":999999}", 400);
    }

    @Test
    void customerEndpointsNeedALogin() throws Exception {
        assertEquals(401, mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/agent/customers")
                .contentType("application/json").content("{}")).andReturn().getResponse().getStatus());
    }
}
