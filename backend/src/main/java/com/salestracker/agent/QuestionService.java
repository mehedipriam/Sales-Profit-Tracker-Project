package com.salestracker.agent;

import com.salestracker.agent.Answer.Column;
import com.salestracker.agent.Answer.Kind;
import com.salestracker.agent.Answer.Stat;
import com.salestracker.agent.Answer.Table;
import com.salestracker.agent.Answer.Tone;
import com.salestracker.auth.AuthUser;
import com.salestracker.common.DateRange;
import com.salestracker.common.Search;
import com.salestracker.customer.Customer;
import com.salestracker.customer.CustomerRepository;
import com.salestracker.order.CustomerRevenue;
import com.salestracker.order.CustomerStatusTotals;
import com.salestracker.order.SaleOrderRepository;
import com.salestracker.product.Product;
import com.salestracker.product.ProductRepository;
import com.salestracker.report.ReportService;
import com.salestracker.report.ReportService.ProductRow;
import com.salestracker.report.ReportService.SummaryResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Answers questions about the business. Read-only, and every figure comes from the same reports the Dashboard and
 * Reports pages use, so the numbers match them exactly. Money questions are for Owner and Admin, like those pages.
 */
@Service
@Transactional(readOnly = true)
public class QuestionService {
    private static final int TOP_CUSTOMERS = 10;
    private static final int LOW_STOCK_ROWS = 15;
    private static final int NAME_CHOICES = 5;

    private final ReportService reports;
    private final SaleOrderRepository orders;
    private final CustomerRepository customers;
    private final ProductRepository products;
    private final Clock clock;

    @Autowired
    public QuestionService(ReportService reports, SaleOrderRepository orders, CustomerRepository customers,
                           ProductRepository products) {
        this(reports, orders, customers, products, Clock.systemDefaultZone());
    }

    QuestionService(ReportService reports, SaleOrderRepository orders, CustomerRepository customers,
                    ProductRepository products, Clock clock) {
        this.reports = reports;
        this.orders = orders;
        this.customers = customers;
        this.products = products;
        this.clock = clock;
    }

    /** Today in the shop's time zone (the server's). */
    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /** The answer card, or a plain message when the question cannot be answered (not allowed, no match, no data). */
    public AgentReply answer(AuthUser user, Intent intent) {
        Intent.Question q = intent.question();
        if (q.needsFinancials() && !user.role().seesFinancials()) {
            return AgentReply.message("Profit, cost, sales and spending figures are for the Owner and Admin only. "
                    + "I can tell you about stock levels and order counts.");
        }
        Period period = Period.of(intent.period(), intent.from(), intent.to(), today());
        Long tenant = user.tenantId();
        return switch (q) {
            case PROFIT -> profit(tenant, period);
            case TOP_PRODUCTS -> topProducts(tenant, period);
            case LOWEST_MARGIN -> lowestMargin(tenant, period);
            case TOP_CUSTOMERS -> topCustomers(tenant, period);
            case CUSTOMER_SPEND -> customerSpend(tenant, period, intent.customerName());
            case LOW_STOCK -> lowStock(tenant);
            case ORDER_COUNTS -> orderCounts(tenant, period);
            case EXPENSES -> expenses(tenant, period);
            case PLATFORMS -> platforms(tenant, period);
        };
    }

    // ---- the questions ----

    private AgentReply profit(Long tenant, Period p) {
        SummaryResponse s = reports.summary(tenant, p.from(), p.to(), null);
        BigDecimal net = s.netProfit();
        boolean loss = net.signum() < 0;
        List<Stat> stats = List.of(
                Stat.money("Revenue", s.realized().revenue()),
                Stat.money("Cost of goods", s.realized().cost()),
                Stat.money("Delivery charged", s.realized().delivery()),
                Stat.money("Expenses", s.expenses().total()),
                Stat.money(loss ? "Net loss" : "Net profit", net, net.signum() > 0 ? Tone.GOOD : loss ? Tone.BAD : Tone.NEUTRAL),
                Stat.count("Paid orders", s.realized().orders()));
        String sentence = (loss ? "The net loss for " : "The net profit for ") + p.label().toLowerCase(Locale.ROOT) + " is "
                + num(net.abs()) + " from " + s.realized().orders() + " paid order" + plural(s.realized().orders()) + ".";
        return answer("Profit or loss", p, stats, null, sentence);
    }

    private AgentReply topProducts(Long tenant, Period p) {
        List<ProductRow> rows = reports.products(tenant, p.from(), p.to(), null).topSellers();
        if (rows.isEmpty()) return noSales(p);
        Table table = new Table(
                List.of(new Column("Product", Kind.TEXT), new Column("Units sold", Kind.COUNT),
                        new Column("Revenue", Kind.MONEY), new Column("Profit", Kind.MONEY)),
                rows.stream().map(r -> List.<Object>of(r.name(), r.quantity(), r.revenue(), r.profit())).toList());
        ProductRow best = rows.get(0);
        return answer("Best-selling products", p,
                List.of(new Stat("Best seller", best.name(), Kind.TEXT, Tone.NEUTRAL), Stat.count("Units sold", best.quantity())),
                table, best.name() + " sold the most for " + p.label().toLowerCase(Locale.ROOT) + ", with "
                        + best.quantity() + " unit" + plural(best.quantity()) + ".");
    }

    private AgentReply lowestMargin(Long tenant, Period p) {
        List<ProductRow> rows = reports.products(tenant, p.from(), p.to(), null).lowestMargin();
        if (rows.isEmpty()) return noSales(p);
        Table table = new Table(
                List.of(new Column("Product", Kind.TEXT), new Column("Margin", Kind.PERCENT),
                        new Column("Profit", Kind.MONEY), new Column("Revenue", Kind.MONEY)),
                rows.stream().map(r -> List.<Object>of(r.name(), r.marginPct() == null ? "-" : r.marginPct(), r.profit(), r.revenue())).toList());
        ProductRow worst = rows.get(0);
        return answer("Lowest-margin products", p,
                List.of(new Stat("Lowest margin", worst.name(), Kind.TEXT, worst.profit().signum() < 0 ? Tone.BAD : Tone.NEUTRAL)),
                table, worst.name() + " has the lowest margin for " + p.label().toLowerCase(Locale.ROOT)
                        + (worst.marginPct() == null ? "." : ", at " + num(worst.marginPct()) + "%."));
    }

    private AgentReply topCustomers(Long tenant, Period p) {
        Map<Long, BigDecimal> spent = spentByCustomer(tenant, p);
        Map<Long, Long> paidOrders = paidOrdersByCustomer(tenant, p);
        List<Map.Entry<Long, BigDecimal>> top = spent.entrySet().stream()
                .sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(TOP_CUSTOMERS).toList();
        if (top.isEmpty()) return noSales(p);
        Map<Long, Customer> byId = customers.findAllById(top.stream().map(Map.Entry::getKey).toList()).stream()
                .collect(Collectors.toMap(Customer::getId, c -> c));
        List<List<Object>> rows = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> e : top) {
            Customer c = byId.get(e.getKey());
            rows.add(List.of(c == null ? "Unknown" : c.getName(), paidOrders.getOrDefault(e.getKey(), 0L), e.getValue()));
        }
        Table table = new Table(List.of(new Column("Customer", Kind.TEXT), new Column("Paid orders", Kind.COUNT),
                new Column("Spent", Kind.MONEY)), rows);
        String best = (String) rows.get(0).get(0);
        return answer("Top customers", p,
                List.of(new Stat("Top customer", best, Kind.TEXT, Tone.NEUTRAL), Stat.money("Spent", top.get(0).getValue())),
                table, best + " spent the most for " + p.label().toLowerCase(Locale.ROOT) + ": " + num(top.get(0).getValue()) + ".");
    }

    private AgentReply customerSpend(Long tenant, Period p, String name) {
        if (name == null || name.isBlank()) return AgentReply.message("Whose spending do you want to know? Tell me the customer's name.");
        List<Customer> found = customers.search(tenant, Search.pattern(name), 0L,
                PageRequest.of(0, NAME_CHOICES + 1, Sort.by("name"))).getContent();
        List<Customer> exact = found.stream().filter(c -> c.getName().equalsIgnoreCase(name.trim())).toList();
        if (exact.size() == 1) found = exact;
        if (found.isEmpty()) return AgentReply.message("No customer matches \"" + name.trim() + "\".");
        if (found.size() > 1) {
            String choices = found.stream().limit(NAME_CHOICES).map(QuestionService::describe).collect(Collectors.joining(", "));
            return AgentReply.message("Which " + name.trim() + " did you mean? " + choices + (found.size() > NAME_CHOICES ? " ..." : ""));
        }
        Customer c = found.get(0);
        BigDecimal spent = spentByCustomer(tenant, p).getOrDefault(c.getId(), BigDecimal.ZERO);
        long paid = paidOrdersByCustomer(tenant, p).getOrDefault(c.getId(), 0L);
        return answer(c.getName() + "'s spending", p,
                List.of(Stat.money("Spent", spent), Stat.count("Paid orders", paid)), null,
                c.getName() + " spent " + num(spent) + " on " + paid + " paid order" + plural(paid) + " for "
                        + p.label().toLowerCase(Locale.ROOT) + ".");
    }

    private AgentReply lowStock(Long tenant) {
        List<Product> low = products.lowStock(tenant, PageRequest.of(0, LOW_STOCK_ROWS));
        Period now = new Period("Right now", null, null);
        if (low.isEmpty()) return AgentReply.message("Nothing is running low on stock.");
        Table table = new Table(List.of(new Column("Product", Kind.TEXT), new Column("In stock", Kind.COUNT),
                new Column("Alert at", Kind.COUNT)),
                low.stream().map(x -> List.<Object>of(x.getName(), x.getStockQty(), x.getLowStockThreshold())).toList());
        long outOf = low.stream().filter(x -> x.getStockQty() <= 0).count();
        List<Stat> stats = new ArrayList<>(List.of(Stat.count("Running low", low.size())));
        if (outOf > 0) stats.add(new Stat("Out of stock", outOf, Kind.COUNT, Tone.BAD));
        return answer("Low stock", now, stats, table, low.size() + " product" + plural(low.size()) + " "
                + (low.size() == 1 ? "is" : "are") + " running low, starting with " + low.get(0).getName() + ".");
    }

    private AgentReply orderCounts(Long tenant, Period p) {
        SummaryResponse s = reports.summary(tenant, p.from(), p.to(), null);
        long paid = s.realized().orders(), pending = s.pending().orders(), returned = s.returnedOrders(), cancelled = s.cancelledOrders();
        List<Stat> stats = List.of(Stat.count("Paid", paid), Stat.count("Pending", pending),
                new Stat("Returned", returned, Kind.COUNT, returned > 0 ? Tone.BAD : Tone.NEUTRAL),
                new Stat("Cancelled", cancelled, Kind.COUNT, cancelled > 0 ? Tone.BAD : Tone.NEUTRAL));
        long total = paid + pending + returned + cancelled;
        return answer("Orders", p, stats, null, "There " + (total == 1 ? "was " : "were ") + total + " order" + plural(total)
                + " for " + p.label().toLowerCase(Locale.ROOT) + ": " + paid + " paid, " + pending + " pending, "
                + returned + " returned and " + cancelled + " cancelled.");
    }

    private AgentReply expenses(Long tenant, Period p) {
        ReportService.ExpenseBreakdown e = reports.summary(tenant, p.from(), p.to(), null).expenses();
        if (e.byType().isEmpty()) return AgentReply.message("No expenses were recorded for " + p.label().toLowerCase(Locale.ROOT) + ".");
        Table table = new Table(List.of(new Column("Type", Kind.TEXT), new Column("Entries", Kind.COUNT), new Column("Total", Kind.MONEY)),
                e.byType().stream().map(t -> List.<Object>of(typeName(t.type().name()), t.count(), t.total())).toList());
        ReportService.ExpenseTypeRow biggest = e.byType().get(0);
        return answer("Expenses", p, List.of(Stat.money("Total expenses", e.total())), table,
                "Expenses for " + p.label().toLowerCase(Locale.ROOT) + " total " + num(e.total()) + ", mostly "
                        + typeName(biggest.type().name()).toLowerCase(Locale.ROOT) + " at " + num(biggest.total()) + ".");
    }

    private AgentReply platforms(Long tenant, Period p) {
        List<ReportService.PlatformRow> rows = reports.summary(tenant, p.from(), p.to(), null).byPlatform();
        if (rows.isEmpty()) return noSales(p);
        Table table = new Table(List.of(new Column("Platform", Kind.TEXT), new Column("Paid orders", Kind.COUNT),
                new Column("Revenue", Kind.MONEY), new Column("Net profit", Kind.MONEY)),
                rows.stream().map(r -> List.<Object>of(r.platformName(), r.orders(), r.revenue(), r.netProfit())).toList());
        ReportService.PlatformRow top = rows.get(0);
        return answer("Results by platform", p,
                List.of(new Stat("Top platform", top.platformName(), Kind.TEXT, Tone.NEUTRAL), Stat.money("Revenue", top.revenue())),
                table, top.platformName() + " brought in the most revenue for " + p.label().toLowerCase(Locale.ROOT)
                        + ": " + num(top.revenue()) + ".");
    }

    // ---- helpers ----

    /** What each customer paid on PAID orders in the period: products plus delivery, as on the Customers page. */
    private Map<Long, BigDecimal> spentByCustomer(Long tenant, Period p) {
        DateRange r = DateRange.of(p.from(), p.to());
        Map<Long, BigDecimal> spent = new HashMap<>();
        for (CustomerRevenue c : orders.paidRevenueByCustomerInRange(tenant, r.from(), r.toExclusive())) {
            spent.merge(c.customerId(), c.revenue(), BigDecimal::add);
        }
        for (CustomerStatusTotals c : orders.paidTotalsByCustomerInRange(tenant, r.from(), r.toExclusive())) {
            spent.merge(c.customerId(), c.delivery(), BigDecimal::add);
        }
        return spent;
    }

    private Map<Long, Long> paidOrdersByCustomer(Long tenant, Period p) {
        DateRange r = DateRange.of(p.from(), p.to());
        return orders.paidTotalsByCustomerInRange(tenant, r.from(), r.toExclusive()).stream()
                .collect(Collectors.toMap(CustomerStatusTotals::customerId, CustomerStatusTotals::orders, Long::sum));
    }

    private static AgentReply noSales(Period p) {
        return AgentReply.message("There are no paid orders for " + p.label().toLowerCase(Locale.ROOT) + ".");
    }

    private static AgentReply answer(String title, Period p, List<Stat> stats, Table table, String sentence) {
        return AgentReply.answer(new Answer(title, p.label(), stats, table, sentence));
    }

    private static String typeName(String type) {
        String s = type.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String plural(long n) {
        return n == 1 ? "" : "s";
    }

    /** 12500.5 as 12,500.5 and 12500 as 12,500, without a currency symbol. */
    static String num(BigDecimal v) {
        return new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.ENGLISH)).format(v);
    }

    /** A customer told apart from others of the same name by the end of their phone number. */
    private static String describe(Customer c) {
        String phone = c.getPhone() == null ? "" : c.getPhone().replaceAll("\\D", "");
        return phone.length() >= 3 ? c.getName() + " (..." + phone.substring(Math.max(0, phone.length() - 4)) + ")" : c.getName();
    }
}
