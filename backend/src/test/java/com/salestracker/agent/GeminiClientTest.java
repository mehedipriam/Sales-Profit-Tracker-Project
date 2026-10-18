package com.salestracker.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salestracker.auth.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** No network and no database: Gemini is replaced by a mock server. */
class GeminiClientTest {
    private static final String URL = "https://gemini.test/v1beta/models/test-model:generateContent";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private GeminiClient client(String key) {
        return new GeminiClient(builder.baseUrl("https://gemini.test").build(), new ObjectMapper(), key, "test-model");
    }

    /** Gemini wraps the model's JSON answer as a string inside candidates[0].content.parts[0].text. */
    private static String answer(String modelJson) throws Exception {
        return new ObjectMapper().writeValueAsString(java.util.Map.of("candidates", java.util.List.of(
                java.util.Map.of("content", java.util.Map.of("parts", java.util.List.of(java.util.Map.of("text", modelJson)))))));
    }

    @Test
    void readsTheStructuredAnswerAndCleansIt() throws Exception {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "secret-key"))
                .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("Rahim 01711-222333 2 ta shirt"))
                .andRespond(withSuccess(answer("""
                        {"customerName":" Rahim ","phone":"০১৭১১-২২২৩৩৩","address":"  ","platform":"Facebook",
                         "deliveryCharge":60,"notes":null,
                         "items":[{"name":"Blue Shirt","quantity":2,"price":900.5}]}
                        """), MediaType.APPLICATION_JSON));

        ExtractedOrder o = client("secret-key").extractOrder("Rahim 01711-222333 2 ta shirt");

        assertEquals("Rahim", o.customerName());
        assertEquals("01711222333", o.phone());
        assertNull(o.address());
        assertEquals("Facebook", o.platform());
        assertEquals(0, o.deliveryCharge().compareTo(new java.math.BigDecimal("60")));
        assertEquals(1, o.items().size());
        assertEquals("Blue Shirt", o.items().get(0).name());
        assertEquals(2, o.items().get(0).quantity());
        assertEquals(0, o.items().get(0).price().compareTo(new java.math.BigDecimal("900.5")));
        server.verify();
    }

    @Test
    void missingFieldsStayNullAndItemsDefaultToEmpty() throws Exception {
        server.expect(requestTo(URL)).andRespond(withSuccess(answer("{\"customerName\":\"Karim\"}"), MediaType.APPLICATION_JSON));

        ExtractedOrder o = client("k").extractOrder("hi");

        assertEquals("Karim", o.customerName());
        assertNull(o.phone());
        assertTrue(o.items().isEmpty());
    }

    @Test
    void aScreenshotIsSentAsInlineDataBeforeTheNote() throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.contents[0].parts[0].inlineData.mimeType").value("image/png"))
                .andExpect(jsonPath("$.contents[0].parts[0].inlineData.data").value(java.util.Base64.getEncoder().encodeToString(png)))
                .andExpect(jsonPath("$.contents[0].parts[1].text").value("Read the order from this screenshot."))
                .andRespond(withSuccess(answer("{\"customerName\":\"Karim\"}"), MediaType.APPLICATION_JSON));

        assertEquals("Karim", client("k").extractOrder(null, new AiClient.Image("image/png", png)).customerName());
        server.verify();
    }

    @Test
    void aTypedNoteTravelsWithTheScreenshot() throws Exception {
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.contents[0].parts[1].text").value("the second one is a gift"))
                .andRespond(withSuccess(answer("{}"), MediaType.APPLICATION_JSON));

        client("k").extractOrder("the second one is a gift", new AiClient.Image("image/jpeg", new byte[]{1}));
        server.verify();
    }

    @Test
    void aRevisionIsReadAsAListOfChangesWithTheDraftAndInstructionSent() throws Exception {
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("{\"lines\":[]}"), org.hamcrest.Matchers.containsString("Instruction: make it 3"))))
                .andExpect(jsonPath("$.generationConfig.responseSchema.properties.changes.items.properties.action.enum[0]").value("SET_QUANTITY"))
                .andRespond(withSuccess(answer("""
                        {"reply":" Now 3. ","customerPhone":"01911-555666","status":"PAID","notes":"",
                         "changes":[{"action":"SET_QUANTITY","line":1,"quantity":3},{"action":"ADD","product":"Red Shirt","quantity":1,"price":800}]}
                        """), MediaType.APPLICATION_JSON));

        DraftRevision r = client("k").reviseDraft("{\"lines\":[]}", "make it 3");

        assertEquals("Now 3.", r.reply());
        assertEquals("01911555666", r.customerPhone());
        assertEquals(com.salestracker.order.OrderStatus.PAID, r.status());
        assertEquals("", r.notes()); // an empty string means "clear the notes"
        assertNull(r.customerName());
        assertEquals(2, r.changes().size());
        assertEquals(DraftRevision.Action.SET_QUANTITY, r.changes().get(0).action());
        assertEquals(1, r.changes().get(0).line());
        assertEquals("Red Shirt", r.changes().get(1).product());
        server.verify();
    }

    @Test
    void anUnknownRevisionActionIsBadGateway() throws Exception {
        server.expect(requestTo(URL)).andRespond(withSuccess(answer("{\"reply\":\"x\",\"changes\":[{\"action\":\"DELETE_EVERYTHING\"}]}"), MediaType.APPLICATION_JSON));
        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(ApiException.class, () -> client("k").reviseDraft("{}", "x")).getStatus());
    }

    @Test
    void withoutAKeyItFailsBeforeCallingGemini() {
        ApiException e = assertThrows(ApiException.class, () -> client("  ").extractOrder("hi"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatus());
        server.verify(); // no request was expected, none was made
    }

    @Test
    void quotaErrorIsReportedAsTooManyRequests() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("customer text here"));

        ApiException e = assertThrows(ApiException.class, () -> client("k").extractOrder("hi"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, e.getStatus());
        assertFalse(e.getMessage().contains("customer text"));
    }

    @Test
    void rejectedKeyIsBadGatewayWithoutARetry() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(ApiException.class, () -> client("k").extractOrder("hi")).getStatus());
        server.verify(); // exactly one request
    }

    @Test
    void aBusyGoogleIsRetriedOnceThenReportedAsBadGateway() throws Exception {
        GeminiClient.RETRY_PAUSE_MS = 0;
        server.expect(ExpectedCount.times(2), requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(ApiException.class, () -> client("k").extractOrder("hi")).getStatus());
        server.verify();
    }

    @Test
    void aServerErrorFollowedByAnAnswerSucceeds() throws Exception {
        GeminiClient.RETRY_PAUSE_MS = 0;
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(URL)).andRespond(withSuccess(answer("{\"customerName\":\"Karim\"}"), MediaType.APPLICATION_JSON));

        assertEquals("Karim", client("k").extractOrder("hi").customerName());
        server.verify();
    }

    @Test
    void emptyOrUnreadableAnswersAreBadGateway() throws Exception {
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"candidates\":[]}", MediaType.APPLICATION_JSON));
        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(ApiException.class, () -> client("k").extractOrder("hi")).getStatus());

        server.reset();
        server.expect(requestTo(URL)).andRespond(withSuccess(answer("not json"), MediaType.APPLICATION_JSON));
        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(ApiException.class, () -> client("k").extractOrder("hi")).getStatus());
    }

    @Test
    void cleanPhoneKeepsDigitsAndALeadingPlus() {
        assertEquals("+8801711222333", GeminiClient.cleanPhone("+880 1711-222333"));
        assertNull(GeminiClient.cleanPhone("none"));
        assertNull(GeminiClient.cleanPhone(null));
    }
}
