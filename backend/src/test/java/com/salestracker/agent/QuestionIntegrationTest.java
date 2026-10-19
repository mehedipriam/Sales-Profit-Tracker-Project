package com.salestracker.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.salestracker.AbstractIntegrationTest;
import com.salestracker.agent.Intent.PeriodName;
import com.salestracker.agent.Intent.Question;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Questions about the business. The AI is stubbed to choose the report; every figure must equal the real reports'. */
class QuestionIntegrationTest extends AbstractIntegrationTest {

    @MockBean AiClient ai;

    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO = LocalDate.of(2026, 9, 30);

    private Tenant owner;
    private long facebook;
    private long imdadH;
    private long imdadK;
    private long rahim;
    private long rice;  // cost 100, sells 150
    private long soap;  // cost 50, sells 40: sold at a loss

    @BeforeEach
    void setUp() throws Exception {
        owner = registerTenant();
        for (JsonNode p : get(owner, "/api/platforms", 200)) {
            if (p.get("name").asText().equals("Facebook Page")) facebook = p.get("id").asLong();
        }
        imdadH = customer("Imdad Hossain", "01711111111");
        imdadK = customer("Imdad Khan", "01722222222");
        rahim = customer("Rahim", "01733333333");
        rice = product("Rice", 100, 150, 100, 5);
        soap = product("Soap", 50, 40, 3, 5);
    }

    // ---- helpers ----

    private long customer(String name, String phone) throws Exception {
        return post(owner, "/api/customers", "{\"name\":\"%s\",\"phone\":\"%s\"}".formatted(name, phone), 201).get("id").asLong();
    }

    private long product(String name, int cost, int price, int stock, int threshold) throws Exception {
        return post(owner, "/api/products", ("{\"name\":\"%s\",\"costPrice\":%d,\"sellingPrice\":%d,\"stockQty\":%d,"
                + "\"lowStockThreshold\":%d}").formatted(name, cost, price, stock, threshold), 201).get("id").asLong();
    }

    private void order(long customerId, long productId, int qty, int price, int delivery, String status, String at) throws Exception {
        post(owner, "/api/orders", """
                {"platformId":%d,"customerId":%d,"status":"%s","orderedAt":"%s","deliveryCharge":%d,
                 "items":[{"productId":%d,"quantity":%d,"soldPrice":%d}]}
                """.formatted(facebook, customerId, status, at, delivery, productId, qty, price), 201);
    }

    private void expense(String type, int amount, String date) throws Exception {
        post(owner, "/api/expenses", "{\"type\":\"%s\",\"amount\":%d,\"expenseDate\":\"%s\"}".formatted(type, amount, date), 201);
    }

    private static Intent ask(Question q, String name) {
        return new Intent(Intent.Kind.QUESTION, q, PeriodName.CUSTOM, FROM, TO, name, null);
    }

    private JsonNode say(Tenant who, Intent intent) throws Exception {
        when(ai.understand(anyString(), any())).thenReturn(intent);
        return post(who, "/api/agent/message", "{\"text\":\"a question\"}", 200);
    }

    private static JsonNode stat(JsonNode answer, String label) {
        for (JsonNode s : answer.get("stats")) if (s.get("label").asText().equals(label)) return s;
        throw new AssertionError("no stat " + label + " in " + answer);
    }

    private static void assertMoney(String expected, JsonNode actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual.decimalValue()), "expected " + expected + " but was " + actual);
    }

    /** September: 3 paid orders, a pending one, a return and a cancellation, plus expenses. */
    private void seedSeptember() throws Exception {
        order(imdadH, rice, 2, 150, 60, "PAID", "2026-09-05T12:00:00");      // revenue 300, cost 200, delivery 60
        order(imdadH, rice, 4, 150, 0, "PAID", "2026-09-30T23:59:00");       // revenue 600, cost 400: last minute of the month
        order(rahim, soap, 3, 40, 0, "PAID", "2026-09-10T12:00:00");         // revenue 120, cost 150: a loss
        order(rahim, rice, 1, 150, 0, "PENDING", "2026-09-11T12:00:00");
        order(imdadK, rice, 1, 150, 0, "RETURNED", "2026-09-12T12:00:00");
        order(imdadK, rice, 1, 150, 0, "CANCELLED", "2026-09-13T12:00:00");
        order(imdadK, rice, 9, 150, 0, "PAID", "2026-10-01T00:00:00");       // the next month: must not count
        expense("ADS", 100, "2026-09-07");
        expense("PACKAGING", 40, "2026-09-08");
    }

    // ---- profit ----

    @Test
    void theProfitCardEqualsTheReportsFigures() throws Exception {
        seedSeptember();
        JsonNode summary = get(owner, "/api/reports/summary?from=2026-09-01&to=2026-09-30", 200);

        JsonNode reply = say(owner, ask(Question.PROFIT, null));
        assertEquals("ANSWER", reply.get("type").asText());
        JsonNode a = reply.get("answer");
        assertMoney(summary.at("/realized/revenue").asText(), stat(a, "Revenue").get("value"));
        assertMoney(summary.at("/realized/cost").asText(), stat(a, "Cost of goods").get("value"));
        assertMoney(summary.at("/realized/delivery").asText(), stat(a, "Delivery charged").get("value"));
        assertMoney(summary.at("/expenses/total").asText(), stat(a, "Expenses").get("value"));
        assertMoney(summary.get("netProfit").asText(), stat(a, "Net profit").get("value"));
        assertEquals(summary.at("/realized/orders").asLong(), stat(a, "Paid orders").get("value").asLong());

        // Known numbers: revenue 1020, cost 750, delivery 60, expenses 140: net 190. The 23:59 order is in, 1 Oct is out.
        assertMoney("1020", stat(a, "Revenue").get("value"));
        assertMoney("190", stat(a, "Net profit").get("value"));
        assertEquals("GOOD", stat(a, "Net profit").get("tone").asText());
        assertEquals(3, stat(a, "Paid orders").get("value").asInt());
        assertTrue(a.get("sentence").asText().contains("190"), a.get("sentence").asText());
    }

    @Test
    void aLossIsShownAsALossInRed() throws Exception {
        order(rahim, soap, 3, 40, 0, "PAID", "2026-09-10T12:00:00"); // revenue 120, cost 150
        JsonNode a = say(owner, ask(Question.PROFIT, null)).get("answer");
        assertMoney("-30", stat(a, "Net loss").get("value"));
        assertEquals("BAD", stat(a, "Net loss").get("tone").asText());
        assertTrue(a.get("sentence").asText().contains("loss"));
    }

    @Test
    void anAiSentenceIsUsedOnlyWhenItsNumbersAreOnTheCard() throws Exception {
        seedSeptember();
        when(ai.wordAnswer(anyString(), anyString())).thenReturn("তোমার নিট লাভ ১৯০ টাকা।");
        assertEquals("তোমার নিট লাভ ১৯০ টাকা।", say(owner, ask(Question.PROFIT, null)).at("/answer/sentence").asText());

        when(ai.wordAnswer(anyString(), anyString())).thenReturn("You made a profit of 99999.");
        String plain = say(owner, ask(Question.PROFIT, null)).at("/answer/sentence").asText();
        assertFalse(plain.contains("99999"));
        assertTrue(plain.contains("190"));

        when(ai.wordAnswer(anyString(), anyString())).thenThrow(new RuntimeException("down"));
        assertTrue(say(owner, ask(Question.PROFIT, null)).at("/answer/sentence").asText().contains("190"));
    }

    // ---- who may ask ----

    @Test
    void staffIsRefusedMoneyQuestionsButCanAskAboutStockAndOrders() throws Exception {
        seedSeptember();
        String email = "staff-" + System.nanoTime() + "@example.com";
        post(owner, "/api/users", "{\"fullName\":\"Rina\",\"email\":\"%s\",\"password\":\"password123\"}".formatted(email), 201);
        JsonNode login = postAnonymous("/api/auth/login", "{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email), 200);
        Tenant staff = new Tenant(owner.id(), "Bearer " + login.get("token").asText());

        for (Question q : List.of(Question.PROFIT, Question.TOP_PRODUCTS, Question.LOWEST_MARGIN, Question.TOP_CUSTOMERS,
                Question.CUSTOMER_SPEND, Question.EXPENSES, Question.PLATFORMS)) {
            JsonNode r = say(staff, ask(q, "Imdad"));
            assertEquals("MESSAGE", r.get("type").asText(), q.name());
            assertTrue(r.get("message").asText().contains("Owner and Admin"), q.name());
            assertTrue(r.path("answer").isEmpty(), q.name());
        }
        JsonNode counts = say(staff, ask(Question.ORDER_COUNTS, null));
        assertEquals("ANSWER", counts.get("type").asText());
        assertEquals(3, stat(counts.get("answer"), "Paid").get("value").asInt());
        assertEquals("ANSWER", say(staff, ask(Question.LOW_STOCK, null)).get("type").asText());
    }

    // ---- the other questions ----

    @Test
    void bestSellingAndLowestMarginProducts() throws Exception {
        seedSeptember();
        JsonNode top = say(owner, ask(Question.TOP_PRODUCTS, null)).get("answer");
        JsonNode rows = top.at("/table/rows");
        assertEquals("Rice", rows.get(0).get(0).asText());
        assertEquals(6, rows.get(0).get(1).asInt());
        assertEquals("Soap", rows.get(1).get(0).asText());

        JsonNode worst = say(owner, ask(Question.LOWEST_MARGIN, null)).get("answer");
        assertEquals("Soap", worst.at("/table/rows/0/0").asText());
        assertMoney("-30", worst.at("/table/rows/0/2"));
        assertEquals("BAD", stat(worst, "Lowest margin").get("tone").asText());
    }

    @Test
    void topCustomersCountProductsAndDeliveryOfPaidOrdersInThePeriod() throws Exception {
        seedSeptember();
        JsonNode a = say(owner, ask(Question.TOP_CUSTOMERS, null)).get("answer");
        assertEquals("Imdad Hossain", a.at("/table/rows/0/0").asText());
        assertEquals(2, a.at("/table/rows/0/1").asInt());
        assertMoney("960", a.at("/table/rows/0/2")); // 300 + 600 + 60 delivery
        assertEquals("Rahim", a.at("/table/rows/1/0").asText());
        assertEquals(2, a.at("/table/rows").size()); // Imdad Khan only has a return, a cancellation and an October order
    }

    @Test
    void oneCustomersSpendingAndTheNameProblems() throws Exception {
        seedSeptember();
        JsonNode a = say(owner, ask(Question.CUSTOMER_SPEND, "Rahim")).get("answer");
        assertMoney("120", stat(a, "Spent").get("value"));
        assertEquals(1, stat(a, "Paid orders").get("value").asInt());

        JsonNode exact = say(owner, ask(Question.CUSTOMER_SPEND, "imdad hossain")).get("answer");
        assertMoney("960", stat(exact, "Spent").get("value"));

        JsonNode several = say(owner, ask(Question.CUSTOMER_SPEND, "Imdad"));
        assertEquals("MESSAGE", several.get("type").asText());
        assertTrue(several.get("message").asText().contains("Imdad Hossain"));
        assertTrue(several.get("message").asText().contains("Imdad Khan"));

        JsonNode nobody = say(owner, ask(Question.CUSTOMER_SPEND, "Zubair"));
        assertEquals("MESSAGE", nobody.get("type").asText());
        assertTrue(nobody.get("message").asText().contains("Zubair"));

        assertEquals("MESSAGE", say(owner, ask(Question.CUSTOMER_SPEND, null)).get("type").asText());
    }

    @Test
    void anotherShopsCustomersAreNeverMatched() throws Exception {
        Tenant other = registerTenant();
        post(other, "/api/customers", "{\"name\":\"Zubair\"}", 201);
        JsonNode r = say(owner, ask(Question.CUSTOMER_SPEND, "Zubair"));
        assertTrue(r.get("message").asText().contains("No customer matches"));
    }

    @Test
    void lowStockOrderCountsExpensesAndPlatforms() throws Exception {
        seedSeptember();
        // Soap: 100 -> bought 3 = stock 0 of threshold 5: low. Rice has plenty.
        JsonNode low = say(owner, ask(Question.LOW_STOCK, null)).get("answer");
        assertEquals("Soap", low.at("/table/rows/0/0").asText());
        assertEquals("Right now", low.get("period").asText());

        JsonNode counts = say(owner, ask(Question.ORDER_COUNTS, null)).get("answer");
        assertEquals(3, stat(counts, "Paid").get("value").asInt());
        assertEquals(1, stat(counts, "Pending").get("value").asInt());
        assertEquals(1, stat(counts, "Returned").get("value").asInt());
        assertEquals(1, stat(counts, "Cancelled").get("value").asInt());

        JsonNode exp = say(owner, ask(Question.EXPENSES, null)).get("answer");
        assertMoney("140", stat(exp, "Total expenses").get("value"));
        assertEquals("Ads", exp.at("/table/rows/0/0").asText());

        JsonNode plat = say(owner, ask(Question.PLATFORMS, null)).get("answer");
        assertEquals("Facebook Page", plat.at("/table/rows/0/0").asText());
        assertMoney("1020", plat.at("/table/rows/0/2"));
    }

    @Test
    void anEmptyPeriodGivesAPlainMessage() throws Exception {
        JsonNode r = say(owner, ask(Question.TOP_PRODUCTS, null));
        assertEquals("MESSAGE", r.get("type").asText());
        assertTrue(r.get("message").asText().contains("no paid orders"));
        assertEquals("MESSAGE", say(owner, ask(Question.EXPENSES, null)).get("type").asText());
    }

    // ---- routing ----

    @Test
    void anOrderMessageStillBecomesADraftAndAnUnclearOneAMessage() throws Exception {
        when(ai.extractOrder(anyString())).thenReturn(new ExtractedOrder("Rahim", "01711222333", "Mirpur", null, null, null,
                List.of(new ExtractedOrder.Item("rice", 2, new BigDecimal("150")))));
        JsonNode draft = say(owner, new Intent(Intent.Kind.ORDER, null, null, null, null, null, null));
        assertEquals("DRAFT", draft.get("type").asText());
        assertEquals(rice, draft.at("/draft/items/0/productId").asLong());

        JsonNode unclear = say(owner, new Intent(Intent.Kind.UNCLEAR, null, null, null, null, null, "Please say more."));
        assertEquals("MESSAGE", unclear.get("type").asText());
        assertEquals("Please say more.", unclear.get("message").asText());

        // A question with no question type is not guessed at.
        assertEquals("MESSAGE", say(owner, new Intent(Intent.Kind.QUESTION, null, null, null, null, null, null)).get("type").asText());
    }

    @Test
    void questionsChangeNothingAndRefusedOnesNeverCallTheAiForWording() throws Exception {
        seedSeptember();
        String before = get(owner, "/api/reports/summary", 200).toString();
        say(owner, ask(Question.PROFIT, null));
        say(owner, ask(Question.TOP_CUSTOMERS, null));
        assertEquals(before, get(owner, "/api/reports/summary", 200).toString());
        clearInvocations(ai);
        say(owner, ask(Question.CUSTOMER_SPEND, "Zubair"));
        verify(ai, never()).wordAnswer(anyString(), anyString());
    }
}
