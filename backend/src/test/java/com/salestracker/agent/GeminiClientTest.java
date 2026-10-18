package com.salestracker.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salestracker.auth.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
    void rejectedKeyAndServerErrorsAreBadGateway() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(ApiException.class, () -> client("k").extractOrder("hi")).getStatus());

        server.reset();
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(ApiException.class, () -> client("k").extractOrder("hi")).getStatus());
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
