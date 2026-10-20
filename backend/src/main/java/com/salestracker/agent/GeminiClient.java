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
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    private static final String REVISE_PROMPT = """
            You help a shop owner correct a draft customer order. You get the current draft as JSON (its lines are \
            numbered from 1) and an instruction that may be in Bengali, English or a mix. Return only the changes the \
            instruction asks for and leave every other field null or the changes list empty. Never invent values. \
            Refer to lines by their number: SET_QUANTITY and SET_PRICE (quantity or price), REMOVE, REPLACE_PRODUCT \
            (product is the new product name). Use ADD, with product, quantity and optional price, for a new product. \
            "make it 3" with one line means that line. Convert Bengali digits to ASCII digits; a phone number is \
            digits only. status is one of PAID, PENDING, RETURNED, CANCELLED. Put a new platform name in platform. \
            An empty string for notes clears them. If the instruction is unclear, refers to a line that does not \
            exist, or asks for something a draft cannot do, change nothing and explain in reply. If a detail needed \
            to do it is missing, change nothing and ask for it in reply. reply is one short sentence in the language \
            of the instruction saying what you did or what you need.""";

    private static final String UNDERSTAND_PROMPT = """
            You are the front door of an assistant for a small shop's sales tracker. Read one typed message, which may be \
            Bengali, English or a mix, and decide what it wants. kind is ORDER when the message looks like a customer's \
            order (a customer, products and quantities, usually a phone number or address). kind is QUESTION when it \
            asks about the business: set question to one of PROFIT (profit or loss, revenue, costs, net result), \
            TOP_PRODUCTS (best-selling or most sold products), LOWEST_MARGIN (products with the lowest margin or that \
            lose money), TOP_CUSTOMERS (best customers, who spent the most), CUSTOMER_SPEND (what one named customer \
            spent or bought: put the name in customerName), LOW_STOCK (what is running low or out of stock), \
            ORDER_COUNTS (how many orders, by status: paid, pending, returned, cancelled), EXPENSES (what was spent, \
            by type) or PLATFORMS (results by platform such as Facebook or Daraz). Set period to TODAY, YESTERDAY, \
            THIS_WEEK, LAST_WEEK, THIS_MONTH, LAST_MONTH, THIS_YEAR, ALL_TIME, or CUSTOM when two dates are given (then \
            fill from and to as yyyy-MM-dd; one date alone is both from and to). If the message names no period use \
            ALL_TIME. kind is CUSTOMER_ADD when asked to add or save a new customer (no products): customerName, phone \
            (digits only, Bengali digits converted), address and platform as given. "Add a customer" with no \
            details at all is still CUSTOMER_ADD, with every detail null: never ask for them in reply. kind is CUSTOMER_FIND when asked to \
            show or look up a saved customer's phone, address, orders or details: customerName, or phone if only a \
            number is given. kind is CUSTOMER_UPDATE when asked to change a saved customer's details, such as \
            "Imdad's address is House 12": customerName is who (as written), and only the details to set go in phone, \
            address, platform or newName (a new name). kind is ORDER_STATUS when asked to mark, set or cancel an \
            order's status: status is PAID (paid, payment received), PENDING, RETURNED (returned, refunded) or \
            CANCELLED; orderRef is the order exactly as written (ORD-2610-0005, or just 4 for "order 4"), or leave it \
            null and put the customer in customerName for "the last order of Imdad". kind is ORDER_FIND when asked to see or check an order's \
            status or a customer's orders without changing anything (including Bengali written in English letters, \
            like "imdad er order gula ki"): orderRef as written, or customerName for "Imdad's orders" or "his last \
            order"; if it only asks what an order's status is and gives a new status, it is ORDER_STATUS instead. \
            kind is EXPENSE_ADD when asked \
            to record a cost: expense.type is DELIVERY, PACKAGING, ADS (advertising, Facebook ads, boosting), \
            PLATFORM_COMMISSION or MISC; expense.amount is a plain number (Bengali digits converted); expense.date is \
            yyyy-MM-dd, worked out from today's date for "today" or "yesterday", and null if no day is said; \
            expense.description says what it was for; expense.orderRef is an order the cost belongs to, as written, \
            else null. kind is STOCK_ADJUST for restocking or changing stock: stock.product is the product name as \
            written; stock.reason is RESTOCK (units arrived or were added), DAMAGE (units damaged, lost or thrown \
            away) or CORRECTION (the count was wrong); stock.quantity is the number of units (always positive) for a \
            restock or damage; for a correction that says what the stock really is ("stock of X is 15") put that in \
            stock.newStock, and for one that says how many to add or remove put the signed number in stock.quantity. \
            kind is HELP when asked what you can do, how to use you, or for help or examples. kind is UNCLEAR for anything else: then reply is one short sentence in the language of the message saying so or asking what they mean. Never \
            invent a value; leave unused fields null.""";

    private static final String WORD_PROMPT = """
            You word the answer to a shop owner's question as ONE short, friendly sentence, in the language of the \
            question (Bengali, English or other). Use only the figures you are given, written exactly as given: do not \
            add, round, convert, compare or calculate any number, and do not add advice or anything else.""";

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
        List<Map<String, Object>> parts = new ArrayList<>();
        if (image != null) {
            parts.add(Map.of("inlineData", Map.of("mimeType", image.mimeType(),
                    "data", Base64.getEncoder().encodeToString(image.bytes()))));
        }
        parts.add(Map.of("text", text == null || text.isBlank() ? "Read the order from this screenshot." : text));
        return readOrder(generate(PROMPT, parts, schema()));
    }

    @Override
    public DraftRevision reviseDraft(String draftSummary, String instruction) {
        List<Map<String, Object>> parts = List.of(Map.of("text", "Current draft:\n" + draftSummary + "\n\nInstruction: " + instruction));
        String out = generate(REVISE_PROMPT, parts, reviseSchema());
        DraftRevision raw;
        try {
            raw = json.readValue(out, DraftRevision.class);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The AI answer could not be read. Try again");
        }
        return new DraftRevision(blankToNull(raw.reply()), blankToNull(raw.customerName()), cleanPhone(raw.customerPhone()),
                blankToNull(raw.customerAddress()), raw.deliveryCharge(), raw.notes() == null ? null : raw.notes().trim(),
                raw.status(), blankToNull(raw.platform()), raw.changes());
    }

    @Override
    public Intent understand(String text, LocalDate today) {
        List<Map<String, Object>> parts = List.of(Map.of("text", "Today is " + today + " (" + today.getDayOfWeek() + ").\n\nMessage: " + text));
        String out = generate(UNDERSTAND_PROMPT, parts, understandSchema());
        JsonNode n;
        try {
            n = json.readTree(out);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The AI answer could not be read. Try again");
        }
        return new Intent(enumOf(Intent.Kind.class, n.path("kind")), enumOf(Intent.Question.class, n.path("question")),
                enumOf(Intent.PeriodName.class, n.path("period")), date(n.path("from")), date(n.path("to")),
                blankToNull(text(n.path("customerName"))), blankToNull(text(n.path("reply"))),
                cleanPhone(text(n.path("phone"))), blankToNull(text(n.path("address"))),
                blankToNull(text(n.path("platform"))), blankToNull(text(n.path("newName"))),
                blankToNull(text(n.path("orderRef"))), enumOf(com.salestracker.order.OrderStatus.class, n.path("status")),
                expense(n.path("expense")), stock(n.path("stock")));
    }

    @Override
    public String wordAnswer(String question, String facts) {
        List<Map<String, Object>> parts = List.of(Map.of("text", "Question: " + question + "\n\nFigures:\n" + facts));
        String out = generate(WORD_PROMPT, parts, Map.of("type", "OBJECT",
                "properties", Map.of("sentence", Map.of("type", "STRING"))));
        try {
            return blankToNull(text(json.readTree(out).path("sentence")));
        } catch (Exception e) {
            return null;
        }
    }

    private static Intent.ExpenseDetails expense(JsonNode n) {
        if (!n.isObject()) return null;
        BigDecimal amount = n.path("amount").isNumber() ? n.path("amount").decimalValue() : null;
        return new Intent.ExpenseDetails(enumOf(com.salestracker.expense.ExpenseType.class, n.path("type")), amount,
                date(n.path("date")), blankToNull(text(n.path("description"))), blankToNull(text(n.path("orderRef"))));
    }

    private static Intent.StockDetails stock(JsonNode n) {
        if (!n.isObject()) return null;
        return new Intent.StockDetails(blankToNull(text(n.path("product"))),
                n.path("quantity").isIntegralNumber() ? n.path("quantity").asInt() : null,
                n.path("newStock").isIntegralNumber() ? n.path("newStock").asInt() : null,
                enumOf(com.salestracker.stock.StockReason.class, n.path("reason")));
    }

    private static String text(JsonNode n) {
        return n.isTextual() ? n.asText() : null;
    }

    private static <E extends Enum<E>> E enumOf(Class<E> type, JsonNode n) {
        try {
            return n.isTextual() ? Enum.valueOf(type, n.asText().trim().toUpperCase()) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static LocalDate date(JsonNode n) {
        try {
            return n.isTextual() ? LocalDate.parse(n.asText().trim()) : null;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Sends one request to Gemini (retrying once on a server error) and returns the JSON text the model answered with. */
    private String generate(String system, List<Map<String, Object>> parts, Map<String, Object> responseSchema) {
        if (apiKey.isEmpty()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "The assistant is not set up: GEMINI_API_KEY is missing");
        }
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", system))),
                "contents", List.of(Map.of("role", "user", "parts", parts)),
                "generationConfig", Map.of(
                        "temperature", 0,
                        "responseMimeType", "application/json",
                        "responseSchema", responseSchema));
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
                } catch (RestClientException again) {
                    throw unreachable();
                }
            } else {
                throw failed(e);
            }
        } catch (RestClientException e) {
            throw unreachable();
        }
        return answerText(response);
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
        if (code == 429) return new ApiException(HttpStatus.TOO_MANY_REQUESTS, quotaMessage(e.getResponseBodyAsString()));
        if (code == 400 || code == 401 || code == 403) return new ApiException(HttpStatus.BAD_GATEWAY, "The AI rejected the request. Check GEMINI_API_KEY and the model name");
        return new ApiException(HttpStatus.BAD_GATEWAY, "The AI service failed. Try again");
    }

    /** No usable answer arrived: the service was unreachable, or it took too long (the read timed out mid-answer). */
    private ApiException unreachable() {
        log.warn("Gemini could not be reached or did not answer in time");
        return new ApiException(HttpStatus.BAD_GATEWAY, "The AI did not answer in time. Try again");
    }

    private static final Pattern RETRY_SECONDS = Pattern.compile("\"retryDelay\"\\s*:\\s*\"(\\d+)(?:\\.\\d+)?s\"|retry in (\\d+)h");

    /**
     * Google's 429 says whether it is the per-minute limit or the daily one, and when it lifts. Only that is read out of
     * the answer; nothing from it is logged or passed on.
     */
    static String quotaMessage(String body) {
        if (body != null && body.contains("PerDay")) {
            Matcher m = RETRY_SECONDS.matcher(body);
            String when = "";
            if (m.find()) {
                long hours = m.group(1) != null ? Math.max(1, Math.round(Long.parseLong(m.group(1)) / 3600.0)) : Long.parseLong(m.group(2));
                when = " It resets in about " + hours + (hours == 1 ? " hour." : " hours.");
            }
            return "The AI's free daily limit is used up." + when + " Switching GEMINI_MODEL to another model or enabling billing on the Gemini key lifts it";
        }
        return "The AI is busy or its quota is used up. Try again shortly";
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String answerText(JsonNode response) {
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
        return out;
    }

    private ExtractedOrder readOrder(String out) {
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

    private static Map<String, Object> reviseSchema() {
        Map<String, Object> change = Map.of("type", "OBJECT", "properties", Map.of(
                "action", Map.of("type", "STRING", "enum", List.of("SET_QUANTITY", "SET_PRICE", "REMOVE", "ADD", "REPLACE_PRODUCT")),
                "line", Map.of("type", "INTEGER", "nullable", true),
                "product", Map.of("type", "STRING", "nullable", true),
                "quantity", Map.of("type", "INTEGER", "nullable", true),
                "price", Map.of("type", "NUMBER", "nullable", true)));
        return Map.of("type", "OBJECT", "properties", Map.of(
                "reply", Map.of("type", "STRING"),
                "customerName", Map.of("type", "STRING", "nullable", true),
                "customerPhone", Map.of("type", "STRING", "nullable", true),
                "customerAddress", Map.of("type", "STRING", "nullable", true),
                "deliveryCharge", Map.of("type", "NUMBER", "nullable", true),
                "notes", Map.of("type", "STRING", "nullable", true),
                "status", Map.of("type", "STRING", "nullable", true, "enum", List.of("PAID", "PENDING", "RETURNED", "CANCELLED")),
                "platform", Map.of("type", "STRING", "nullable", true),
                "changes", Map.of("type", "ARRAY", "items", change)));
    }

    private static Map<String, Object> understandSchema() {
        Map<String, Object> text = Map.of("type", "STRING", "nullable", true);
        return Map.of("type", "OBJECT", "properties", Map.ofEntries(
                Map.entry("kind", Map.of("type", "STRING", "enum", List.of("ORDER", "QUESTION", "CUSTOMER_ADD", "CUSTOMER_FIND",
                        "CUSTOMER_UPDATE", "ORDER_STATUS", "ORDER_FIND", "EXPENSE_ADD", "STOCK_ADJUST", "HELP", "UNCLEAR"))),
                Map.entry("orderRef", text),
                Map.entry("expense", Map.of("type", "OBJECT", "nullable", true, "properties", Map.of(
                        "type", Map.of("type", "STRING", "nullable", true,
                                "enum", List.of("DELIVERY", "PACKAGING", "ADS", "PLATFORM_COMMISSION", "MISC")),
                        "amount", Map.of("type", "NUMBER", "nullable", true),
                        "date", text, "description", text, "orderRef", text))),
                Map.entry("stock", Map.of("type", "OBJECT", "nullable", true, "properties", Map.of(
                        "product", text,
                        "quantity", Map.of("type", "INTEGER", "nullable", true),
                        "newStock", Map.of("type", "INTEGER", "nullable", true),
                        "reason", Map.of("type", "STRING", "nullable", true, "enum", List.of("RESTOCK", "DAMAGE", "CORRECTION"))))),
                Map.entry("status", Map.of("type", "STRING", "nullable", true, "enum", List.of("PAID", "PENDING", "RETURNED", "CANCELLED"))),
                Map.entry("question", Map.of("type", "STRING", "nullable", true, "enum", List.of("PROFIT", "TOP_PRODUCTS",
                        "LOWEST_MARGIN", "TOP_CUSTOMERS", "CUSTOMER_SPEND", "LOW_STOCK", "ORDER_COUNTS", "EXPENSES", "PLATFORMS"))),
                Map.entry("period", Map.of("type", "STRING", "nullable", true, "enum", List.of("TODAY", "YESTERDAY", "THIS_WEEK",
                        "LAST_WEEK", "THIS_MONTH", "LAST_MONTH", "THIS_YEAR", "ALL_TIME", "CUSTOM"))),
                Map.entry("from", text), Map.entry("to", text), Map.entry("customerName", text), Map.entry("reply", text),
                Map.entry("phone", text), Map.entry("address", text), Map.entry("platform", text), Map.entry("newName", text)),
                "required", List.of("kind"));
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
