package com.salestracker.report;

import com.salestracker.common.DateRange;
import com.salestracker.customer.Customer;
import com.salestracker.customer.CustomerRepository;
import com.salestracker.order.OrderItem;
import com.salestracker.order.OrderStatus;
import com.salestracker.order.SaleOrder;
import com.salestracker.order.SaleOrderRepository;
import com.salestracker.platform.Platform;
import com.salestracker.platform.PlatformRepository;
import com.salestracker.product.Product;
import com.salestracker.product.ProductRepository;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * CSV export of the filtered report: one row per order line, every status included (a Status column lets Excel
 * users filter to paid orders, which is what the on-screen totals count). Streams page by page so a large history
 * never sits in memory.
 */
@Service
@Transactional(readOnly = true)
public class ReportExportService {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final List<String> HEADER = List.of("Order date", "Order #", "Platform", "Customer", "Status",
            "Product", "Quantity", "Unit sold price", "Unit cost", "Revenue", "Cost", "Profit");
    /** Characters that make a spreadsheet treat a cell as a formula. */
    private static final String FORMULA_TRIGGERS = "=+-@\t\r";

    /** Filters validated up front, so a bad request still gets a normal 400 before any CSV bytes are written. */
    public record Filters(DateRange range, long platformId) {}

    private final ReportService reports;
    private final SaleOrderRepository orders;
    private final PlatformRepository platforms;
    private final CustomerRepository customers;
    private final ProductRepository products;
    private final EntityManager entityManager;
    private final int pageSize;

    public ReportExportService(ReportService reports, SaleOrderRepository orders, PlatformRepository platforms,
                               CustomerRepository customers, ProductRepository products, EntityManager entityManager,
                               @Value("${app.export.page-size:500}") int pageSize) {
        this.reports = reports;
        this.orders = orders;
        this.platforms = platforms;
        this.customers = customers;
        this.products = products;
        this.entityManager = entityManager;
        this.pageSize = pageSize;
    }

    public Filters filters(Long tenantId, LocalDate from, LocalDate to, Long platformId) {
        return new Filters(DateRange.of(from, to), reports.platformFilter(tenantId, platformId));
    }

    public void writeCsv(Long tenantId, Filters filters, OutputStream out) throws IOException {
        Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        w.write('\uFEFF'); // BOM: without it Excel misreads UTF-8 (the taka sign, Bangla names)
        writeRow(w, HEADER.stream().map(ReportExportService::text).toList());

        Pageable pageable = PageRequest.of(0, pageSize, Sort.by(Sort.Order.asc("orderedAt"), Sort.Order.asc("id")));
        Page<SaleOrder> page;
        do {
            page = orders.search(tenantId, filters.platformId(), List.of(OrderStatus.values()),
                    filters.range().from(), filters.range().toExclusive(), pageable);

            Map<Long, Platform> platformById = byId(platforms.findAllById(ids(page, SaleOrder::getPlatformId)), Platform::getId);
            Map<Long, Customer> customerById = byId(customers.findAllById(ids(page, SaleOrder::getCustomerId)), Customer::getId);
            Set<Long> productIds = page.stream().flatMap(o -> o.getItems().stream())
                    .map(OrderItem::getProductId).collect(Collectors.toSet());
            Map<Long, Product> productById = byId(products.findAllById(productIds), Product::getId);

            for (SaleOrder o : page) {
                for (OrderItem i : o.getItems()) {
                    writeRow(w, List.of(
                            text(o.getOrderedAt().format(DATE_TIME)),
                            String.valueOf(o.getId()),
                            text(platformById.get(o.getPlatformId()).getName()),
                            text(customerById.get(o.getCustomerId()).getName()),
                            text(o.getStatus().name()),
                            text(productById.get(i.getProductId()).getName()),
                            String.valueOf(i.getQuantity()),
                            money(i.getSoldPrice()),
                            money(i.getCostPriceSnapshot()),
                            money(i.lineRevenue()),
                            money(i.lineCost()),
                            money(i.lineProfit())));
                }
            }
            w.flush();
            entityManager.clear(); // keep memory flat across pages
            pageable = page.nextPageable();
        } while (page.hasNext());
        w.flush();
    }

    private static void writeRow(Writer w, List<String> cells) throws IOException {
        w.write(String.join(",", cells));
        w.write("\r\n");
    }

    private static String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * Always-quoted text cell. A leading =, +, -, @, tab or CR would be executed as a formula by Excel/Sheets,
     * and names here are user-typed, so those get a leading apostrophe (OWASP's CSV-injection guidance).
     */
    static String text(String value) {
        String v = value == null ? "" : value;
        if (!v.isEmpty() && FORMULA_TRIGGERS.indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return '"' + v.replace("\"", "\"\"") + '"';
    }

    private static Set<Long> ids(Page<SaleOrder> page, Function<SaleOrder, Long> id) {
        return page.stream().map(id).collect(Collectors.toSet());
    }

    private static <T> Map<Long, T> byId(Iterable<T> values, Function<T, Long> id) {
        Map<Long, T> map = new java.util.HashMap<>();
        values.forEach(v -> map.put(id.apply(v), v));
        return map;
    }
}
