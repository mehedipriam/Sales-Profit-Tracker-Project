package com.salestracker.report;

import com.salestracker.auth.ApiException;
import com.salestracker.common.DateRange;
import com.salestracker.dashboard.DashboardService;
import com.salestracker.dashboard.DashboardService.Totals;
import com.salestracker.expense.DayExpenses;
import com.salestracker.expense.ExpenseRepository;
import com.salestracker.expense.ExpenseType;
import com.salestracker.expense.ExpenseTypeTotals;
import com.salestracker.expense.PlatformExpenses;
import com.salestracker.order.DayTotals;
import com.salestracker.order.OrderStatus;
import com.salestracker.order.PlatformTotals;
import com.salestracker.order.ProductTotals;
import com.salestracker.order.SaleOrderRepository;
import com.salestracker.order.StatusTotals;
import com.salestracker.platform.Platform;
import com.salestracker.platform.PlatformRepository;
import com.salestracker.product.Product;
import com.salestracker.product.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Transactional(readOnly = true)
public class ReportService {
    /** Ranges up to this many days are charted per day; longer ones per month. */
    private static final int MAX_DAILY_POINTS = 62;
    private static final int TOP_N = 10;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * profit is gross (revenue - cost of goods); netProfit also subtracts the platform's expenses. A platform with
     * expenses but no paid orders (say a returned parcel's delivery) still gets a row, with zero revenue.
     */
    public record PlatformRow(Long platformId, String platformName, long orders,
                              BigDecimal revenue, BigDecimal cost, BigDecimal profit,
                              BigDecimal expenses, BigDecimal netProfit) {}

    public record ExpenseTypeRow(ExpenseType type, long count, BigDecimal total) {}

    /**
     * Expenses that reduce realized profit: everything in the range except expenses tied to a still-pending order.
     * unallocated is the part not tied to any order (ads, overhead), which has no platform; it is only counted when
     * no platform filter is set.
     */
    public record ExpenseBreakdown(BigDecimal total, BigDecimal unallocated, List<ExpenseTypeRow> byType) {}

    /**
     * Same status rule as the dashboard: realized = PAID, pending = PENDING (expected),
     * RETURNED/CANCELLED only counted. byPlatform covers realized (PAID) orders only.
     */
    public record SummaryResponse(LocalDate from, LocalDate to, Long platformId,
                                  Totals realized, Totals pending, long returnedOrders, long cancelledOrders,
                                  ExpenseBreakdown expenses, BigDecimal netProfit,
                                  List<PlatformRow> byPlatform) {}

    /** period = the day, or the first day of the month when granularity is "month". profit is gross. */
    public record TrendPoint(LocalDate period, long orders, BigDecimal revenue, BigDecimal cost, BigDecimal profit,
                             BigDecimal expenses, BigDecimal netProfit) {}

    public record TrendResponse(String granularity, List<TrendPoint> points) {}

    /** marginPct = profit / revenue x 100; null when the product earned no revenue. */
    public record ProductRow(Long productId, String name, long quantity, BigDecimal revenue, BigDecimal cost,
                             BigDecimal profit, BigDecimal marginPct) {}

    public record ProductsResponse(List<ProductRow> topSellers, List<ProductRow> lowestMargin) {}

    private final SaleOrderRepository orders;
    private final PlatformRepository platforms;
    private final ProductRepository products;
    private final ExpenseRepository expenses;

    public ReportService(SaleOrderRepository orders, PlatformRepository platforms, ProductRepository products,
                         ExpenseRepository expenses) {
        this.orders = orders;
        this.platforms = platforms;
        this.products = products;
        this.expenses = expenses;
    }

    public SummaryResponse summary(Long tenantId, LocalDate from, LocalDate to, Long platformId) {
        DateRange range = DateRange.of(from, to);
        long pid = platformFilter(tenantId, platformId);

        Map<OrderStatus, StatusTotals> byStatus = new EnumMap<>(OrderStatus.class);
        orders.totalsByStatus(tenantId, pid, range.from(), range.toExclusive())
                .forEach(t -> byStatus.put(t.status(), t));

        List<PlatformTotals> perPlatform = orders.totalsByPlatform(
                tenantId, OrderStatus.PAID, pid, range.from(), range.toExclusive());
        List<ExpenseTypeTotals> byType = expenses.realizedByType(tenantId, pid, range.from().toLocalDate(),
                range.toExclusive().toLocalDate());
        Map<Long, BigDecimal> spendByPlatform = expenses.realizedByPlatform(tenantId, pid,
                        range.from().toLocalDate(), range.toExclusive().toLocalDate()).stream()
                .collect(Collectors.toMap(PlatformExpenses::platformId, PlatformExpenses::total));

        Set<Long> platformIds = new HashSet<>(spendByPlatform.keySet());
        perPlatform.forEach(t -> platformIds.add(t.platformId()));
        Map<Long, Platform> names = platforms.findAllById(platformIds).stream()
                .collect(Collectors.toMap(Platform::getId, Function.identity()));
        Map<Long, PlatformTotals> paid = perPlatform.stream()
                .collect(Collectors.toMap(PlatformTotals::platformId, Function.identity()));

        List<PlatformRow> rows = platformIds.stream().map(id -> {
                    PlatformTotals t = paid.get(id);
                    BigDecimal revenue = t == null ? BigDecimal.ZERO : t.revenue();
                    BigDecimal cost = t == null ? BigDecimal.ZERO : t.cost();
                    BigDecimal spend = spendByPlatform.getOrDefault(id, BigDecimal.ZERO);
                    BigDecimal gross = revenue.subtract(cost);
                    return new PlatformRow(id, names.get(id).getName(), t == null ? 0 : t.orders(),
                            revenue, cost, gross, spend, gross.subtract(spend));
                })
                .sorted(Comparator.comparing(PlatformRow::revenue).reversed().thenComparing(PlatformRow::platformName))
                .toList();

        BigDecimal totalSpend = byType.stream().map(ExpenseTypeTotals::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal allocated = spendByPlatform.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        ExpenseBreakdown breakdown = new ExpenseBreakdown(totalSpend, totalSpend.subtract(allocated),
                byType.stream().map(t -> new ExpenseTypeRow(t.type(), t.count(), t.total()))
                        .sorted(Comparator.comparing(ExpenseTypeRow::total).reversed()).toList());

        Totals realized = DashboardService.totals(byStatus, OrderStatus.PAID);
        return new SummaryResponse(from, to, platformId,
                realized,
                DashboardService.totals(byStatus, OrderStatus.PENDING),
                DashboardService.totals(byStatus, OrderStatus.RETURNED).orders(),
                DashboardService.totals(byStatus, OrderStatus.CANCELLED).orders(),
                breakdown, realized.profit().subtract(totalSpend),
                rows);
    }

    /** Realized (PAID) revenue, cost and gross profit plus expenses and net profit over time, zero-filled so gaps show as gaps in activity. */
    public TrendResponse trend(Long tenantId, LocalDate from, LocalDate to, Long platformId) {
        DateRange range = DateRange.of(from, to);
        long pid = platformFilter(tenantId, platformId);
        List<DayTotals> days = orders.totalsByDay(tenantId, OrderStatus.PAID, pid, range.from(), range.toExclusive());
        Map<LocalDate, BigDecimal> spend = expenses.realizedByDay(tenantId, pid, range.from().toLocalDate(),
                        range.toExclusive().toLocalDate()).stream()
                .collect(Collectors.toMap(DayExpenses::day, DayExpenses::total));

        // An open-ended range starts and ends at the first and last day that has a sale or an expense.
        LocalDate firstActive = Stream.concat(days.stream().map(DayTotals::day), spend.keySet().stream())
                .min(Comparator.naturalOrder()).orElse(null);
        LocalDate lastActive = Stream.concat(days.stream().map(DayTotals::day), spend.keySet().stream())
                .max(Comparator.naturalOrder()).orElse(null);
        LocalDate start = from != null ? from : firstActive;
        LocalDate end = to != null ? to : lastActive;
        if (start == null || end == null) {
            return new TrendResponse("day", List.of());
        }

        Map<LocalDate, DayTotals> byDay = days.stream().collect(Collectors.toMap(DayTotals::day, Function.identity()));
        boolean daily = ChronoUnit.DAYS.between(start, end) + 1 <= MAX_DAILY_POINTS;

        List<TrendPoint> points = new ArrayList<>();
        if (daily) {
            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                DayTotals t = byDay.get(d);
                BigDecimal spent = spend.getOrDefault(d, BigDecimal.ZERO);
                points.add(t == null ? point(d, 0, BigDecimal.ZERO, BigDecimal.ZERO, spent)
                        : point(d, t.orders(), t.revenue(), t.cost(), spent));
            }
        } else {
            Map<YearMonth, long[]> orderCount = new HashMap<>();
            Map<YearMonth, BigDecimal[]> money = new HashMap<>(); // revenue, cost, expenses
            for (DayTotals t : days) {
                YearMonth ym = YearMonth.from(t.day());
                orderCount.computeIfAbsent(ym, k -> new long[1])[0] += t.orders();
                BigDecimal[] m = money.computeIfAbsent(ym, k -> zeros());
                m[0] = m[0].add(t.revenue());
                m[1] = m[1].add(t.cost());
            }
            spend.forEach((day, total) -> {
                BigDecimal[] m = money.computeIfAbsent(YearMonth.from(day), k -> zeros());
                m[2] = m[2].add(total);
            });
            for (YearMonth ym = YearMonth.from(start); !ym.isAfter(YearMonth.from(end)); ym = ym.plusMonths(1)) {
                BigDecimal[] m = money.getOrDefault(ym, zeros());
                points.add(point(ym.atDay(1), orderCount.getOrDefault(ym, new long[1])[0], m[0], m[1], m[2]));
            }
        }
        return new TrendResponse(daily ? "day" : "month", points);
    }

    /** Top sellers by units and the lowest-margin (or loss-making) products, over realized (PAID) orders. */
    public ProductsResponse products(Long tenantId, LocalDate from, LocalDate to, Long platformId) {
        DateRange range = DateRange.of(from, to);
        long pid = platformFilter(tenantId, platformId);
        List<ProductTotals> totals = orders.totalsByProduct(
                tenantId, OrderStatus.PAID, pid, range.from(), range.toExclusive());

        Map<Long, Product> names = products.findAllById(totals.stream().map(ProductTotals::productId).toList())
                .stream().collect(Collectors.toMap(Product::getId, Function.identity()));

        List<ProductRow> rows = totals.stream().map(t -> {
            BigDecimal profit = t.revenue().subtract(t.cost());
            BigDecimal margin = t.revenue().signum() > 0
                    ? profit.multiply(HUNDRED).divide(t.revenue(), 2, RoundingMode.HALF_UP) : null;
            return new ProductRow(t.productId(), names.get(t.productId()).getName(), t.quantity(),
                    t.revenue(), t.cost(), profit, margin);
        }).toList();

        List<ProductRow> topSellers = rows.stream()
                .sorted(Comparator.comparingLong(ProductRow::quantity).reversed()
                        .thenComparing(Comparator.comparing(ProductRow::revenue).reversed())
                        .thenComparing(ProductRow::name))
                .limit(TOP_N).toList();

        List<ProductRow> lowestMargin = rows.stream()
                .sorted(Comparator.comparing(ReportService::marginSortKey)
                        .thenComparing(ProductRow::profit).thenComparing(ProductRow::name))
                .limit(TOP_N).toList();

        return new ProductsResponse(topSellers, lowestMargin);
    }

    /** Products sold for nothing at a cost rank as the worst; zero-revenue, zero-cost ones as the best. */
    private static BigDecimal marginSortKey(ProductRow r) {
        if (r.marginPct() != null) return r.marginPct();
        return r.profit().signum() < 0 ? BigDecimal.valueOf(-1_000_000) : BigDecimal.valueOf(1_000_000);
    }

    private static TrendPoint point(LocalDate period, long orders, BigDecimal revenue, BigDecimal cost,
                                    BigDecimal expenses) {
        BigDecimal gross = revenue.subtract(cost);
        return new TrendPoint(period, orders, revenue, cost, gross, expenses, gross.subtract(expenses));
    }

    private static BigDecimal[] zeros() {
        return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
    }

    /** 0 means "all platforms"; a platform id must belong to the caller's business. */
    long platformFilter(Long tenantId, Long platformId) {
        if (platformId != null && !platforms.existsByIdAndTenantId(platformId, tenantId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown platform");
        }
        return platformId == null ? 0L : platformId;
    }
}
