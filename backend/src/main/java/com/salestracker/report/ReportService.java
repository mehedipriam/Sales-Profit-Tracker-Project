package com.salestracker.report;

import com.salestracker.auth.ApiException;
import com.salestracker.common.DateRange;
import com.salestracker.dashboard.DashboardService;
import com.salestracker.dashboard.DashboardService.Totals;
import com.salestracker.order.OrderStatus;
import com.salestracker.order.PlatformTotals;
import com.salestracker.order.SaleOrderRepository;
import com.salestracker.order.StatusTotals;
import com.salestracker.platform.Platform;
import com.salestracker.platform.PlatformRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ReportService {

    public record PlatformRow(Long platformId, String platformName, long orders,
                              BigDecimal revenue, BigDecimal cost, BigDecimal profit) {}

    /**
     * Same status rule as the dashboard: realized = PAID, pending = PENDING (expected),
     * RETURNED/CANCELLED only counted. byPlatform covers realized (PAID) orders only.
     */
    public record SummaryResponse(LocalDate from, LocalDate to, Long platformId,
                                  Totals realized, Totals pending, long returnedOrders, long cancelledOrders,
                                  List<PlatformRow> byPlatform) {}

    private final SaleOrderRepository orders;
    private final PlatformRepository platforms;

    public ReportService(SaleOrderRepository orders, PlatformRepository platforms) {
        this.orders = orders;
        this.platforms = platforms;
    }

    public SummaryResponse summary(Long tenantId, LocalDate from, LocalDate to, Long platformId) {
        DateRange range = DateRange.of(from, to);
        if (platformId != null && !platforms.existsByIdAndTenantId(platformId, tenantId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown platform");
        }
        long pid = platformId == null ? 0L : platformId;

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
}
