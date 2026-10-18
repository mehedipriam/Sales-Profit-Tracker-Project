package com.salestracker.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salestracker.AbstractIntegrationTest;
import com.salestracker.agent.DraftRevision.Action;
import com.salestracker.agent.DraftRevision.Change;
import com.salestracker.order.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The AI is stubbed with the changes it would ask for; applying them, and checking the result again, is the real code. */
class RevisionIntegrationTest extends AbstractIntegrationTest {

    @MockBean AiClient ai;

    private Tenant tenant;
    private long blue;
    private long red;

    @BeforeEach
    void setUp() throws Exception {
        tenant = registerTenant();
        blue = post(tenant, "/api/products", "{\"name\":\"Blue Shirt\",\"costPrice\":500,\"sellingPrice\":900,\"stockQty\":10}", 201).get("id").asLong();
        red = post(tenant, "/api/products", "{\"name\":\"Red Shirt\",\"costPrice\":500,\"sellingPrice\":800,\"stockQty\":10}", 201).get("id").asLong();
    }

    private static DraftRevision plan(String reply, Change... changes) {
        return new DraftRevision(reply, null, null, null, null, null, null, null, List.of(changes));
    }

    private static Change change(Action action, Integer line, String product, Integer qty, String price) {
        return new Change(action, line, product, qty, price == null ? null : new BigDecimal(price));
    }

    /** A first draft for Rahim (new customer) with a Blue Shirt x2 at 900, the way the chat would hold it. */
    private ObjectNode draftState() throws Exception {
        when(ai.extractOrder(anyString())).thenReturn(new ExtractedOrder("Rahim", "01711222333", "Mirpur 10", null,
                new BigDecimal("60"), null, List.of(new ExtractedOrder.Item("blue shirt", 2, new BigDecimal("900")))));
        JsonNode d = post(tenant, "/api/agent/draft", "{\"text\":\"order\"}", 200);
        ObjectNode state = json.createObjectNode();
        state.set("customer", d.get("customer"));
        state.set("platformId", d.get("platformId"));
        state.set("status", d.get("status"));
        state.set("deliveryCharge", d.get("deliveryCharge"));
        state.set("notes", d.get("notes"));
        ArrayNode lines = state.putArray("lines");
        for (JsonNode i : d.get("items")) {
            lines.addObject().put("text", i.get("text").asText()).set("productId", i.get("productId"));
            ObjectNode l = (ObjectNode) lines.get(lines.size() - 1);
            l.set("quantity", i.get("quantity"));
            l.set("soldPrice", i.get("soldPrice"));
        }
        return state;
    }

    private JsonNode revise(ObjectNode state, String instruction) throws Exception {
        ObjectNode body = json.createObjectNode();
        body.put("instruction", instruction);
        body.set("draft", state);
        return post(tenant, "/api/agent/revise", body.toString(), 200);
    }

    private static boolean hasIssue(JsonNode draft, String level, String part) {
        for (JsonNode i : draft.get("issues")) {
            if (i.get("level").asText().equals(level) && i.get("message").asText().contains(part)) return true;
        }
        return false;
    }

    @Test
    void quantityDeliveryAndStatusChangeButTheProductStays() throws Exception {
        ObjectNode state = draftState();
        when(ai.reviseDraft(anyString(), anyString())).thenReturn(new DraftRevision("Now 3, paid, delivery 100.", null, null, null,
                new BigDecimal("100"), null, OrderStatus.PAID, null, List.of(change(Action.SET_QUANTITY, 1, null, 3, null))));

        JsonNode r = revise(state, "make it 3, paid, delivery 100");

        assertEquals("Now 3, paid, delivery 100.", r.get("reply").asText());
        JsonNode d = r.get("draft");
        assertEquals(blue, d.at("/items/0/productId").asLong());
        assertEquals(3, d.at("/items/0/quantity").asInt());
        assertEquals(900, d.at("/items/0/soldPrice").asInt());
        assertEquals("PAID", d.get("status").asText());
        assertEquals(100, d.get("deliveryCharge").asInt());
        assertTrue(d.get("canConfirm").asBoolean());
        assertEquals("Rahim", d.at("/customer/name").asText());
    }

    @Test
    void theAiIsToldTheNumberedLinesAndTheInstruction() throws Exception {
        ObjectNode state = draftState();
        when(ai.reviseDraft(anyString(), anyString())).thenReturn(plan("ok"));

        revise(state, "  make it 3  ");

        ArgumentCaptor<String> summary = ArgumentCaptor.forClass(String.class);
        verify(ai).reviseDraft(summary.capture(), org.mockito.ArgumentMatchers.eq("make it 3"));
        JsonNode s = json.readTree(summary.getValue());
        assertEquals(1, s.at("/lines/0/line").asInt());
        assertEquals("Blue Shirt", s.at("/lines/0/product").asText());
        assertEquals(2, s.at("/lines/0/quantity").asInt());
        assertEquals("Rahim", s.at("/customer/name").asText());
        assertEquals("Facebook Page", s.get("platform").asText());
        assertFalse(s.at("/customer/alreadySaved").asBoolean());
    }

    @Test
    void aLineCanBeRemovedAndAnotherAdded() throws Exception {
        ObjectNode state = draftState();
        when(ai.reviseDraft(anyString(), anyString())).thenReturn(plan("Swapped.",
                change(Action.REMOVE, 1, null, null, null), change(Action.ADD, null, "red shirts", 2, null)));

        JsonNode d = revise(state, "remove the blue one and add 2 red").get("draft");

        assertEquals(1, d.get("items").size());
        assertEquals(red, d.at("/items/0/productId").asLong());
        assertEquals(2, d.at("/items/0/quantity").asInt());
        assertEquals(800, d.at("/items/0/soldPrice").asInt()); // no price given: the product's list price
        assertTrue(hasIssue(d, "WARNING", "No price for Red Shirt"));
    }

    @Test
    void replacingAProductDropsTheOldPrice() throws Exception {
        ObjectNode state = draftState();
        when(ai.reviseDraft(anyString(), anyString())).thenReturn(plan("Red instead.",
                change(Action.REPLACE_PRODUCT, 1, "red shirt", null, null)));

        JsonNode d = revise(state, "red instead of blue").get("draft");

        assertEquals(red, d.at("/items/0/productId").asLong());
        assertEquals(2, d.at("/items/0/quantity").asInt());
        assertEquals(800, d.at("/items/0/soldPrice").asInt());
    }

    @Test
    void removingEveryLineLeavesADraftThatCannotBeConfirmed() throws Exception {
        ObjectNode state = draftState();
        when(ai.reviseDraft(anyString(), anyString())).thenReturn(plan("Removed.", change(Action.REMOVE, 1, null, null, null)));

        JsonNode d = revise(state, "remove it").get("draft");

        assertEquals(0, d.get("items").size());
        assertFalse(d.get("canConfirm").asBoolean());
    }

    @Test
    void aMissingLineNumberChangesNothingAndSaysSo() throws Exception {
        ObjectNode state = draftState();
        when(ai.reviseDraft(anyString(), anyString())).thenReturn(plan("Tried.", change(Action.SET_QUANTITY, 5, null, 9, null)));

        JsonNode d = revise(state, "make line 5 nine").get("draft");

        assertEquals(2, d.at("/items/0/quantity").asInt());
        assertTrue(hasIssue(d, "WARNING", "There is no line 5"));
    }

    @Test
    void aNewCustomerCanGetAPhoneAddressAndNameFixed() throws Exception {
        ObjectNode state = draftState();
        when(ai.reviseDraft(anyString(), anyString())).thenReturn(new DraftRevision("Updated.", "Rahim Uddin", "01911555666",
                "Dhanmondi 5", null, "call first", null, null, List.of()));

        JsonNode d = revise(state, "fix the customer").get("draft");

        assertTrue(d.at("/customer/isNew").asBoolean());
        assertEquals("Rahim Uddin", d.at("/customer/name").asText());
        assertEquals("01911555666", d.at("/customer/phone").asText());
        assertEquals("Dhanmondi 5", d.at("/customer/address").asText());
        assertEquals("call first", d.get("notes").asText());
    }

    @Test
    void givingAPhoneThatIsOnFileSwitchesToThatCustomer() throws Exception {
        long saved = post(tenant, "/api/customers", "{\"name\":\"Karim\",\"phone\":\"01811999888\",\"address\":\"Uttara\"}", 201).get("id").asLong();
        ObjectNode state = draftState();
        when(ai.reviseDraft(anyString(), anyString())).thenReturn(new DraftRevision("It is Karim.", null, "+8801811999888",
                null, null, null, null, null, List.of()));

        JsonNode d = revise(state, "the phone is 01811-999888").get("draft");

        assertFalse(d.at("/customer/isNew").asBoolean());
        assertEquals(saved, d.at("/customer/id").asLong());
        assertEquals("Karim", d.at("/customer/name").asText());
        assertFalse(hasIssue(d, "WARNING", "belongs to")); // Rahim was only the draft's guess
    }

    @Test
    void aSavedCustomerIsNotRewrittenFromTheChat() throws Exception {
        long saved = post(tenant, "/api/customers", "{\"name\":\"Karim\",\"phone\":\"01811999888\",\"address\":\"Uttara\"}", 201).get("id").asLong();
        ObjectNode state = draftState();
        ObjectNode customer = state.putObject("customer");
        customer.put("id", saved).put("name", "Karim").put("phone", "01811999888").put("address", "Uttara").put("isNew", false);
        when(ai.reviseDraft(anyString(), anyString())).thenReturn(new DraftRevision("Done.", null, null, "Mirpur 1", null,
                null, null, null, List.of()));

        JsonNode d = revise(state, "the address is Mirpur 1").get("draft");

        assertEquals(saved, d.at("/customer/id").asLong());
        assertEquals("Uttara", d.at("/customer/address").asText());
        assertTrue(hasIssue(d, "WARNING", "already saved"));
        verify(ai).reviseDraft(org.mockito.ArgumentMatchers.contains("\"alreadySaved\":true"), anyString());
    }

    @Test
    void anotherShopsProductIdIsNotTrusted() throws Exception {
        Tenant other = registerTenant();
        long foreign = post(other, "/api/products", "{\"name\":\"Secret Item\",\"costPrice\":1,\"sellingPrice\":5}", 201).get("id").asLong();
        ObjectNode state = draftState();
        ObjectNode line = (ObjectNode) state.get("lines").get(0);
        line.put("productId", foreign).put("text", "laptop");
        when(ai.reviseDraft(anyString(), anyString())).thenReturn(plan("Nothing."));

        JsonNode d = revise(state, "hello").get("draft");

        assertTrue(d.at("/items/0/productId").isNull());
        assertEquals("NOT_FOUND", d.at("/items/0/match").asText());
        assertFalse(d.get("canConfirm").asBoolean());
        verify(ai).reviseDraft(org.mockito.ArgumentMatchers.argThat(s -> !s.contains("Secret Item")), anyString());
    }

    @Test
    void anUnknownPlatformKeepsTheCurrentOneWithAWarning() throws Exception {
        ObjectNode state = draftState();
        when(ai.reviseDraft(anyString(), anyString())).thenReturn(new DraftRevision("Tried.", null, null, null, null, null,
                null, "TikTok", List.of()));

        JsonNode d = revise(state, "platform is TikTok").get("draft");

        assertEquals("Facebook Page", d.get("platformName").asText());
        assertTrue(hasIssue(d, "WARNING", "no platform called TikTok"));
    }

    @Test
    void aBlankInstructionOrMissingDraftIsRejected() throws Exception {
        ObjectNode state = draftState();
        ObjectNode body = json.createObjectNode();
        body.put("instruction", "  ");
        body.set("draft", state);
        post(tenant, "/api/agent/revise", body.toString(), 400);
        post(tenant, "/api/agent/revise", "{\"instruction\":\"make it 3\"}", 400);
        org.mockito.Mockito.verify(ai, org.mockito.Mockito.never()).reviseDraft(any(), any());
    }
}
