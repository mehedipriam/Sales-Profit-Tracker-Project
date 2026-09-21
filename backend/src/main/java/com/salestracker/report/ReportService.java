package com.salestracker.report;

import com.salestracker.auth.ApiException;
import com.salestracker.common.DateRange;
import com.salestracker.dashboard.DashboardService;
import com.salestracker.dashboard.DashboardService.Totals;
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

@Service
@Transactional(readOnly = true)
public class ReportService {
    /** Ranges up to this many days are charted per day; longer ones per month. */
    private static final int MAX_DAILY_POINTS = 62;
    private static final int TOP_N = 10;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public record PlatformRow(Long platformId, String platformName, long orders,
                              BigDecimal revenue, BigDecimal cost, BigDecimal profit) {}

    /**
     * Same status rule as the dashboard: realized = PAID, pending = PENDING (expected),
     * RETURNED/CANCELLED only counted. byPlatform covers realized (PAID) orders only.
     */
    public record SummaryResponse(LocalDate from, LocalDate to, Long platformId,
                                  Totals realized, Totals pending, long returnedOrders, long cancelledOrders,
                                  List<PlatformRow> byPlatform) {}

    /** period = the day, or the first day of the month when granularity is "month". */
    public record TrendPoint(LocalDate period, long orders, BigDecimal revenue, BigDecimal cost, BigDecimal profit) {}

    public record TrendResponse(String granularity, List<TrendPoint> points) {}

    /** marginPct = profit / revenue x 100; null when the product earned no revenue. */
    public record ProductRow(Long productId, String name, long quantity, BigDecimal revenue, BigDecimal cost,
                             BigDecimal profit, BigDecimal marginPct) {}

    public record ProductsResponse(List<ProductRow> topSellers, List<ProductRow> lowestMargin) {}

    private final SaleOrderRepository orders;
    private final PlatformRepository platforms;
    private final ProductRepository products;

    public ReportService(SaleOrderRepository orders, PlatformRepository platforms, ProductRepository products) {
        this.orders = orders;
        this.platforms = platforms;
        this.products = products;
    }

    public SummaryResponse summary(Long tenantId, LocalDate from, LocalDate to, Long platformId) {
        DateRange range = DateRange.of(from, to);
        long pid = platformFilter(tenantId, platformId);

        Map<OrderStatus, StatusTotals> byStatus = new EnumMap<>(OrderStatus.class);
        orders.totalsByStatus(tenantId, pid, range.from(), range.toExclusive())
                .forEach(t -> byStatus.put(t.status(), t));

        List<PlatformTotals> perPlatform = orders.totalsByPlatform(
                tenantId, OrderStatus.PAID, pid, range.from(), range.toExclusive());
        Map<Long, Platform> names = platforms.findAllById(
                        perPlatform.stream().map(PlatformTotals::platformId).toList()).stream()
                .collect(Collectors.toMap(Platform::getId, Function.identity()));

        List<PlatformRow> rows = perPlatform.stream()
                .map(t -> new PlatformRow(t.platformId(), names.get(t.platformId()).getName(), t.orders(),
                        t.revenue(), t.cost(), t.revenue().subtract(t.cost())))
                .sorted(Comparator.comparing(PlatformRow::revenue).reversed())
                .toList();

        return new SummaryResponse(from, to, platformId,
                DashboardService.totals(byStatus, OrderStatus.PAID),
                DashboardService.totals(byStatus, OrderStatus.PENDING),
                DashboardService.totals(byStatus, OrderStatus.RETURNED).orders(),
                DashboardService.totals(byStatus, OrderStatus.CANCELLED).orders(),
                rows);
    }

    /** Realized (PAID) revenue, cost and profit over time, zero-filled so gaps show as gaps in activity. */
    public TrendResponse trend(Long tenantId, LocalDate from, LocalDate to, Long platformId) {
        DateRange range = DateRange.of(from, to);
        long pid = platformFilter(tenantId, platformId);
        List<DayTotals> days = orders.totalsByDay(tenantId, OrderStatus.PAID, pid, range.from(), range.toExclusive());

        LocalDate start = from != null ? from : days.isEmpty() ? null : days.get(0).day();
        LocalDate end = to != null ? to : days.isEmpty() ? null : days.get(days.size() - 1).day();
        if (start == null || end == null) {
            return new TrendResponse("day", List.of());
        }

        Map<LocalDate, DayTotals> byDay = days.stream().collect(Collectors.toMap(DayTotals::day, Function.identity()));
        boolean daily = ChronoUnit.DAYS.between(start, end) + 1 <= MAX_DAILY_POINTS;

        List<TrendPoint> points = new ArrayList<>();
        if (daily) {
            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                DayTotals t = byDay.get(d);
                points.add(t == null ? point(d, 0, BigDecimal.ZERO, BigDecimal.ZERO)
                        : point(d, t.orders(), t.revenue(), t.cost()));
            }
        } else {
            Map<YearMonth, long[]> orderCount = new HashMap<>();
            Map<YearMonth, BigDecimal[]> money = new HashMap<>();
            for (DayTotals t : days) {
                YearMonth ym = YearMonth.from(t.day());
                orderCount.computeIfAbsent(ym, k -> new long[1])[0] += t.orders();
                BigDecimal[] m = money.computeIfAbsent(ym, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                m[0] = m[0].add(t.revenue());
                m[1] = m[1].add(t.cost());
            }
            for (YearMonth ym = YearMonth.from(start); !ym.isAfter(YearMonth.from(end)); ym = ym.plusMonths(1)) {
                BigDecimal[] m = money.getOrDefault(ym, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                points.add(point(ym.atDay(1), orderCount.getOrDefault(ym, new long[1])[0], m[0], m[1]));
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

    private static TrendPoint point(LocalDate period, long orders, BigDecimal revenue, BigDecimal cost) {
        return new TrendPoint(period, orders, revenue, cost, revenue.subtract(cost));
    }

    /** 0 means "all platforms"; a platform id must belong to the caller's business. */
    long platformFilter(Long tenantId, Long platformId) {
        if (platformId != null && !platforms.existsByIdAndTenantId(platformId, tenantId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown platform");
        }
        return platformId == null ? 0L : platformId;
    }
}
