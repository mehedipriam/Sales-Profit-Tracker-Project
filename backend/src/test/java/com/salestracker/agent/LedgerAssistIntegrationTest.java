package com.salestracker.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.salestracker.AbstractIntegrationTest;
import com.salestracker.agent.Intent.ExpenseDetails;
import com.salestracker.agent.Intent.Kind;
import com.salestracker.agent.Intent.StockDetails;
import com.salestracker.expense.ExpenseType;
import com.salestracker.stock.StockReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** Recording expenses and changing stock from the chat. The AI is stubbed to name the details. */
class LedgerAssistIntegrationTest extends AbstractIntegrationTest {

    @MockBean AiClient ai;

    private Tenant owner;
    private long facebook;
    private long customer;
    private long beans;   // stock 5
    private long cow;     // stock 12, "Cow & Gate"
    private long plain;   // does not track stock

    @BeforeEach
    void setUp() throws Exception {
        owner = registerTenant();
        for (JsonNode p : get(owner, "/api/platforms", 200)) {
            if (p.get("name").asText().equals("Facebook Page")) facebook = p.get("id").asLong();
        }
        customer = post(owner, "/api/customers", "{\"name\":\"Imdad Hossain\"}", 201).get("id").asLong();
        beans = product("Beans Pudding", 5);
        cow = product("Cow & Gate", 12);
        plain = post(owner, "/api/products", "{\"name\":\"Gift Wrap\",\"costPrice\":5,\"sellingPrice\":10}", 201).get("id").asLong();
    }

    // ---- helpers ----

    private long product(String name, int stock) throws Exception {
        return post(owner, "/api/products", ("{\"name\":\"%s\",\"costPrice\":80,\"sellingPrice\":120,\"stockQty\":%d}")
                .formatted(name, stock), 201).get("id").asLong();
    }

    private static Intent expense(ExpenseType type, String amount, LocalDate date, String description, String orderRef) {
        return new Intent(Kind.EXPENSE_ADD, null, null, null, null, null, null, null, null, null, null, null, null,
                new ExpenseDetails(type, amount == null ? null : new BigDecimal(amount), date, description, orderRef), null);
    }

    private static Intent stock(String product, Integer quantity, Integer newStock, StockReason reason) {
        return new Intent(Kind.STOCK_ADJUST, null, null, null, null, null, null, null, null, null, null, null, null, null,
                new StockDetails(product, quantity, newStock, reason));
    }

    private JsonNode say(Tenant who, Intent intent) throws Exception {
        when(ai.understand(anyString(), any())).thenReturn(intent);
        return post(who, "/api/agent/message", "{\"text\":\"a message\"}", 200);
    }

    private Tenant staff() throws Exception {
        String email = "staff-" + System.nanoTime() + "@example.com";
        post(owner, "/api/users", "{\"fullName\":\"Rina\",\"email\":\"%s\",\"password\":\"password123\"}".formatted(email), 201);
        JsonNode login = postAnonymous("/api/auth/login", "{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email), 200);
        return new Tenant(owner.id(), "Bearer " + login.get("token").asText());
    }

    private int stockOf(long productId) throws Exception {
        for (JsonNode p : get(owner, "/api/products", 200).get("content")) if (p.get("id").asLong() == productId) return p.get("stockQty").asInt();
        throw new AssertionError("no product " + productId);
    }

    private JsonNode order() throws Exception {
        return post(owner, "/api/orders", ("{\"platformId\":%d,\"customerId\":%d,\"status\":\"PAID\","
                + "\"items\":[{\"productId\":%d,\"quantity\":1,\"soldPrice\":120}]}").formatted(facebook, customer, cow), 201);
    }

    private static boolean said(JsonNode r, String part) {
        return r.get("type").asText().equals("MESSAGE") && r.get("message").asText().contains(part);
    }

    // ---- expenses ----

    @Test
    void anExpenseCardDefaultsToTodayAndSavesNothing() throws Exception {
        JsonNode r = say(owner, expense(ExpenseType.ADS, "500", null, "Facebook ads", null));
        assertEquals("EXPENSE", r.get("type").asText());
        JsonNode c = r.get("expense");
        assertEquals("ADS", c.get("type").asText());
        assertEquals(500, c.get("amount").asInt());
        assertEquals(LocalDate.now().toString(), c.get("date").asText());
        assertEquals("Facebook ads", c.get("description").asText());
        assertTrue(c.get("orderId").isNull());
        assertEquals(0, get(owner, "/api/expenses", 200).get("totalElements").asInt());
    }

    @Test
    void anExpenseCanBeTiedToAnOrderByItsNumber() throws Exception {
        JsonNode o = order();
        JsonNode c = say(owner, expense(ExpenseType.PACKAGING, "120", LocalDate.of(2026, 9, 3), null, o.get("orderNo").asText())).get("expense");
        assertEquals(o.get("id").asLong(), c.get("orderId").asLong());
        assertEquals(o.get("orderNo").asText(), c.get("orderNo").asText());
        assertEquals("Imdad Hossain", c.get("customerName").asText());
        assertEquals("2026-09-03", c.get("date").asText());

        assertTrue(said(say(owner, expense(ExpenseType.PACKAGING, "120", null, null, "ORD-2601-9999")), "No order matches"));
    }

    @Test
    void missingOrBadDetailsGetAPlainQuestion() throws Exception {
        assertTrue(said(say(owner, expense(null, "100", null, null, null)), "What kind of expense"));
        assertTrue(said(say(owner, expense(ExpenseType.ADS, null, null, null, null)), "How much"));
        assertTrue(said(say(owner, expense(ExpenseType.ADS, "0", null, null, null)), "above zero"));
        assertTrue(said(say(owner, expense(ExpenseType.ADS, "-50", null, null, null)), "above zero"));
        assertTrue(said(say(owner, expense(ExpenseType.ADS, "10.123", null, null, null)), "not valid"));
        assertTrue(said(say(owner, new Intent(Kind.EXPENSE_ADD, null, null, null, null, null, null)), "What kind of expense"));
    }

    @Test
    void aRepeatOfTheSameExpenseOnTheSameDayIsWarnedAbout() throws Exception {
        post(owner, "/api/expenses", "{\"type\":\"ADS\",\"amount\":500,\"expenseDate\":\"%s\"}".formatted(LocalDate.now()), 201);
        JsonNode c = say(owner, expense(ExpenseType.ADS, "500", null, null, null)).get("expense");
        assertEquals(1, c.get("warnings").size());
        assertTrue(c.get("warnings").get(0).asText().contains("not a duplicate"));
        assertEquals(0, say(owner, expense(ExpenseType.ADS, "600", null, null, null)).at("/expense/warnings").size());
    }

    @Test
    void confirmingSavesTheExpenseAndProfitIncludesIt() throws Exception {
        JsonNode o = order();
        String body = "{\"type\":\"ADS\",\"amount\":500,\"expenseDate\":\"%s\",\"description\":\"Facebook ads\"}".formatted(LocalDate.now());
        JsonNode saved = post(owner, "/api/agent/expenses", body, 201);
        assertEquals(500, saved.get("amount").asInt());
        assertEquals(1, get(owner, "/api/expenses", 200).get("totalElements").asInt());
        assertEquals(500, get(owner, "/api/reports/summary", 200).at("/expenses/total").asInt());

        post(owner, "/api/agent/expenses", ("{\"type\":\"PACKAGING\",\"amount\":120,\"expenseDate\":\"%s\",\"orderId\":%d}")
                .formatted(LocalDate.now(), o.get("id").asLong()), 201);
        assertEquals(620, get(owner, "/api/reports/summary", 200).at("/expenses/total").asInt());

        post(owner, "/api/agent/expenses", "{\"type\":\"ADS\",\"amount\":0,\"expenseDate\":\"2026-01-01\"}", 400);
        post(owner, "/api/agent/expenses", "{\"type\":\"ADS\",\"amount\":-5,\"expenseDate\":\"2026-01-01\"}", 400);
        post(owner, "/api/agent/expenses", "{\"type\":\"ADS\",\"amount\":5,\"expenseDate\":\"2026-01-01\",\"orderId\":999999}", 400);
    }

    @Test
    void staffAreRefusedExpensesInTheChatAndOnTheEndpoint() throws Exception {
        Tenant staff = staff();
        assertTrue(said(say(staff, expense(ExpenseType.ADS, "500", null, null, null)), "Owner and Admin only"));
        post(staff, "/api/agent/expenses", "{\"type\":\"ADS\",\"amount\":500,\"expenseDate\":\"2026-01-01\"}", 403);
        assertEquals(0, get(owner, "/api/expenses", 200).get("totalElements").asInt());
    }

    // ---- stock ----

    @Test
    void aRestockCardShowsStockBeforeAndAfterAndSavesNothing() throws Exception {
        JsonNode r = say(owner, stock("beans pudding", 20, null, StockReason.RESTOCK));
        assertEquals("STOCK", r.get("type").asText());
        JsonNode c = r.get("stock");
        assertEquals(beans, c.get("productId").asLong());
        assertEquals("Beans Pudding", c.get("productName").asText());
        assertEquals(20, c.get("change").asInt());
        assertEquals(5, c.get("before").asInt());
        assertEquals(25, c.get("after").asInt());
        assertEquals("RESTOCK", c.get("reason").asText());
        assertEquals(5, stockOf(beans));
    }

    @Test
    void damagedGoodsAreRemovedAndNeverBelowZero() throws Exception {
        JsonNode c = say(owner, stock("Cow Gate", 2, null, StockReason.DAMAGE)).get("stock");
        assertEquals(cow, c.get("productId").asLong());
        assertEquals(-2, c.get("change").asInt());
        assertEquals(10, c.get("after").asInt());

        assertTrue(said(say(owner, stock("Beans Pudding", 9, null, StockReason.DAMAGE)), "Only 5 of Beans Pudding"));
        assertEquals(5, stockOf(beans));
    }

    @Test
    void aCorrectionCanSetTheCountOrMoveItBySomeUnits() throws Exception {
        JsonNode set = say(owner, stock("Beans Pudding", null, 15, StockReason.CORRECTION)).get("stock");
        assertEquals(10, set.get("change").asInt());
        assertEquals(15, set.get("after").asInt());

        JsonNode down = say(owner, stock("Beans Pudding", null, 2, StockReason.CORRECTION)).get("stock");
        assertEquals(-3, down.get("change").asInt());

        assertEquals(-3, say(owner, stock("Beans Pudding", -3, null, StockReason.CORRECTION)).at("/stock/change").asInt());
        assertTrue(said(say(owner, stock("Beans Pudding", null, 5, StockReason.CORRECTION)), "Nothing to change"));
        assertTrue(said(say(owner, stock("Beans Pudding", null, -1, StockReason.CORRECTION)), "below zero"));
        assertTrue(said(say(owner, stock("Beans Pudding", -9, null, StockReason.CORRECTION)), "Only 5"));
    }

    @Test
    void productAndQuantityProblemsGiveAPlainMessage() throws Exception {
        assertTrue(said(say(owner, stock("Laptop", 3, null, StockReason.RESTOCK)), "No product matches"));
        assertTrue(said(say(owner, stock("Gift Wrap", 3, null, StockReason.RESTOCK)), "does not track stock"));
        assertTrue(said(say(owner, stock("Beans Pudding", 0, null, StockReason.RESTOCK)), "at least 1"));
        assertTrue(said(say(owner, stock("Beans Pudding", -4, null, StockReason.RESTOCK)), "at least 1"));
        assertTrue(said(say(owner, stock("Beans Pudding", null, null, StockReason.RESTOCK)), "How many"));
        assertTrue(said(say(owner, stock("Beans Pudding", null, null, StockReason.CORRECTION)), "real stock"));
        assertTrue(said(say(owner, stock("Beans Pudding", 3, null, null)), "restock, damaged"));
        assertTrue(said(say(owner, stock(null, 3, null, StockReason.RESTOCK)), "Which product"));
        assertTrue(plain > 0);
    }

    @Test
    void twoCloseProductsAreNotGuessedBetween() throws Exception {
        product("Blue Shirt", 4);
        product("Red Shirt", 4);
        JsonNode r = say(owner, stock("shirt", 3, null, StockReason.RESTOCK));
        assertTrue(said(r, "Which product did you mean"));
        assertTrue(r.get("message").asText().contains("Blue Shirt") && r.get("message").asText().contains("Red Shirt"));
    }

    @Test
    void confirmingLogsTheChangeLikeTheStockPage() throws Exception {
        JsonNode done = post(owner, "/api/agent/stock",
                "{\"productId\":%d,\"change\":20,\"reason\":\"RESTOCK\",\"before\":5}".formatted(beans), 201);
        assertEquals(25, done.get("stockAfter").asInt());
        assertEquals(25, stockOf(beans));
        JsonNode log = get(owner, "/api/stock/adjustments?productId=" + beans + "&reason=RESTOCK", 200).get("content");
        assertEquals(1, log.size());
        assertEquals(20, log.get(0).get("change").asInt());

        post(owner, "/api/agent/stock", "{\"productId\":%d,\"change\":-2,\"reason\":\"DAMAGE\",\"before\":25}".formatted(beans), 201);
        assertEquals(23, stockOf(beans));
    }

    @Test
    void aStaleOrImpossibleStockCardIsRefused() throws Exception {
        String stale = post(owner, "/api/agent/stock", "{\"productId\":%d,\"change\":3,\"reason\":\"RESTOCK\",\"before\":9}".formatted(beans), 409)
                .get("message").asText();
        assertTrue(stale.contains("is now 5, not 9"));
        post(owner, "/api/agent/stock", "{\"productId\":%d,\"change\":-6,\"reason\":\"DAMAGE\",\"before\":5}".formatted(beans), 400);
        post(owner, "/api/agent/stock", "{\"productId\":%d,\"change\":0,\"reason\":\"CORRECTION\",\"before\":5}".formatted(beans), 400);
        post(owner, "/api/agent/stock", "{\"productId\":%d,\"change\":-3,\"reason\":\"RESTOCK\",\"before\":5}".formatted(beans), 400);
        post(owner, "/api/agent/stock", "{\"productId\":%d,\"change\":3,\"reason\":\"ORDER\",\"before\":5}".formatted(beans), 400);
        post(owner, "/api/agent/stock", "{\"productId\":%d,\"change\":3,\"reason\":\"RESTOCK\"}".formatted(beans), 400);
        assertEquals(5, stockOf(beans));
    }

    @Test
    void staffCanRestockButAnotherShopsProductIsOutOfReach() throws Exception {
        Tenant staff = staff();
        assertEquals("STOCK", say(staff, stock("Beans Pudding", 5, null, StockReason.RESTOCK)).get("type").asText());
        post(staff, "/api/agent/stock", "{\"productId\":%d,\"change\":5,\"reason\":\"RESTOCK\",\"before\":5}".formatted(beans), 201);
        assertEquals(10, stockOf(beans));

        Tenant other = registerTenant();
        long theirs = post(other, "/api/products", "{\"name\":\"Soap\",\"costPrice\":1,\"sellingPrice\":2,\"stockQty\":3}", 201).get("id").asLong();
        assertTrue(said(say(owner, stock("Soap", 1, null, StockReason.RESTOCK)), "No product matches"));
        post(owner, "/api/agent/stock", "{\"productId\":%d,\"change\":1,\"reason\":\"RESTOCK\",\"before\":3}".formatted(theirs), 404);
    }

    @Test
    void theNewEndpointsNeedALogin() throws Exception {
        for (String url : new String[]{"/api/agent/expenses", "/api/agent/stock"}) {
            assertEquals(401, mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                    .contentType("application/json").content("{}")).andReturn().getResponse().getStatus(), url);
        }
    }
}
