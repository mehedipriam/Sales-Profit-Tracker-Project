package com.salestracker.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salestracker.auth.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/** Calls Google's Gemini API from the backend, so the key never reaches the browser. */
@Component
public class GeminiClient implements AiClient {
    private static final String BENGALI_DIGITS = "০১২৩৪৫৬৭৮৯";

    private static final String PROMPT = """
            You read customer order messages from Facebook Messenger for a small shop. The text may be Bengali, \
            English or a mix of both. Extract only what is actually written. Never guess or invent a value: \
            use null for anything that is missing. Convert Bengali digits to ASCII digits. The phone number is \
            digits only (keep a leading + if present). Item names are copied as written. Quantity defaults to 1 \
            only when an item is clearly ordered without a number. Prices and the delivery charge are plain \
            numbers without currency symbols. Put anything else useful (delivery instructions, gift notes) in notes. \
            If you are given a screenshot of a conversation, read all of it: the order is what the customer and the \
            shop settled on, so a later message that corrects a quantity, price or address wins over an earlier one. \
            The shop's own name and phone are not the customer's.""";

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);
    /** Mutable only so a test does not have to wait. */
    static long RETRY_PAUSE_MS = 1000;

    private final RestClient http;
    private final ObjectMapper json;
    private final String apiKey;
    private final String model;

    @Autowired
    public GeminiClient(ObjectMapper json,
                        @Value("${app.gemini.api-key:}") String apiKey,
                        @Value("${app.gemini.model}") String model,
                        @Value("${app.gemini.base-url}") String baseUrl) {
        this(timedClient(baseUrl), json, apiKey, model);
    }

    /** Takes a ready client so tests can point it at a mock server. */
    GeminiClient(RestClient http, ObjectMapper json, String apiKey, String model) {
        this.http = http;
        this.json = json;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
    }

    private static RestClient timedClient(String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(40).toMillis());
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    public ExtractedOrder extractOrder(String text) {
        return extractOrder(text, null);
    }

    @Override
    public ExtractedOrder extractOrder(String text, Image image) {
        if (apiKey.isEmpty()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "The order agent is not set up: GEMINI_API_KEY is missing");
        }
        List<Map<String, Object>> parts = new ArrayList<>();
        if (image != null) {
            parts.add(Map.of("inlineData", Map.of("mimeType", image.mimeType(),
                    "data", Base64.getEncoder().encodeToString(image.bytes()))));
        }
        parts.add(Map.of("text", text == null || text.isBlank() ? "Read the order from this screenshot." : text));
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", PROMPT))),
                "contents", List.of(Map.of("role", "user", "parts", parts)),
                "generationConfig", Map.of(
                        "temperature", 0,
                        "responseMimeType", "application/json",
                        "responseSchema", schema()));
        JsonNode response;
        try {
            response = post(body);
        } catch (RestClientResponseException e) {
            // Google answers 5xx now and then when it is busy; one more try usually gets through.
            if (e.getStatusCode().is5xxServerError()) {
                log.warn("Gemini answered {}; retrying once", e.getStatusCode().value());
                sleep(RETRY_PAUSE_MS);
                try {
                    response = post(body);
                } catch (RestClientResponseException again) {
                    throw failed(again);
                } catch (ResourceAccessException again) {
                    throw unreachable();
                }
            } else {
                throw failed(e);
            }
        } catch (ResourceAccessException e) {
            throw unreachable();
        }
        return parse(response);
    }

    private JsonNode post(Map<String, Object> body) {
        return http.post().uri("/v1beta/models/{model}:generateContent", model)
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().body(JsonNode.class);
    }

    /** Never put the request or response text in a message or the log: it holds customer details. */
    private ApiException failed(RestClientResponseException e) {
        int code = e.getStatusCode().value();
        log.warn("Gemini request failed with status {}", code);
        if (code == 429) return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "The AI is busy or its quota is used up. Try again shortly");
        if (code == 400 || code == 401 || code == 403) return new ApiException(HttpStatus.BAD_GATEWAY, "The AI rejected the request. Check GEMINI_API_KEY and the model name");
        return new ApiException(HttpStatus.BAD_GATEWAY, "The AI service failed. Try again");
    }

    private ApiException unreachable() {
        log.warn("Gemini could not be reached");
        return new ApiException(HttpStatus.BAD_GATEWAY, "Could not reach the AI service. Try again");
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private ExtractedOrder parse(JsonNode response) {
        String out = "";
        if (response != null) {
            // Skip "thought" parts some models add before the answer.
            for (JsonNode part : response.at("/candidates/0/content/parts")) {
                if (!part.path("thought").asBoolean(false) && part.hasNonNull("text")) {
                    out = part.get("text").asText();
                    break;
                }
            }
        }
        if (out.isBlank()) {
            // E.g. the answer was blocked by a safety filter.
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The AI gave no answer. Try rewording the message");
        }
        ExtractedOrder raw;
        try {
            raw = json.readValue(out, ExtractedOrder.class);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The AI answer could not be read. Try again");
        }
        return new ExtractedOrder(blankToNull(raw.customerName()), cleanPhone(raw.phone()), blankToNull(raw.address()),
                blankToNull(raw.platform()), raw.deliveryCharge(), blankToNull(raw.notes()), raw.items());
    }

    /** Digits only (a leading + is kept), with any Bengali digits the model left turned into ASCII. */
    static String cleanPhone(String phone) {
        if (phone == null) return null;
        StringBuilder sb = new StringBuilder();
        for (char c : phone.trim().toCharArray()) {
            int bn = BENGALI_DIGITS.indexOf(c);
            if (bn >= 0) sb.append((char) ('0' + bn));
            else if (c >= '0' && c <= '9') sb.append(c);
            else if (c == '+' && sb.isEmpty()) sb.append(c);
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static Map<String, Object> schema() {
        Map<String, Object> item = Map.of("type", "OBJECT", "properties", Map.of(
                "name", Map.of("type", "STRING"),
                "quantity", Map.of("type", "INTEGER", "nullable", true),
                "price", Map.of("type", "NUMBER", "nullable", true)));
        return Map.of("type", "OBJECT", "properties", Map.of(
                "customerName", Map.of("type", "STRING", "nullable", true),
                "phone", Map.of("type", "STRING", "nullable", true),
                "address", Map.of("type", "STRING", "nullable", true),
                "platform", Map.of("type", "STRING", "nullable", true),
                "deliveryCharge", Map.of("type", "NUMBER", "nullable", true),
                "notes", Map.of("type", "STRING", "nullable", true),
                "items", Map.of("type", "ARRAY", "items", item)));
    }
}
