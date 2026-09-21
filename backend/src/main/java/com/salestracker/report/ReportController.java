package com.salestracker.report;

import com.salestracker.auth.AuthUser;
import com.salestracker.report.ReportService.ProductsResponse;
import com.salestracker.report.ReportService.SummaryResponse;
import com.salestracker.report.ReportService.TrendResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    /** from/to are inclusive ISO dates (yyyy-MM-dd); omit either for an open-ended range. */
    @GetMapping("/summary")
    public SummaryResponse summary(@AuthenticationPrincipal AuthUser user,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                   @RequestParam(required = false) Long platformId) {
        return service.summary(user.tenantId(), from, to, platformId);
    }

    /** Revenue, cost and profit per day (ranges up to 62 days) or per month, for the trend chart. */
    @GetMapping("/trend")
    public TrendResponse trend(@AuthenticationPrincipal AuthUser user,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                               @RequestParam(required = false) Long platformId) {
        return service.trend(user.tenantId(), from, to, platformId);
    }

    /** Top 10 best-selling and top 10 lowest-margin products. */
    @GetMapping("/products")
    public ProductsResponse products(@AuthenticationPrincipal AuthUser user,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                     @RequestParam(required = false) Long platformId) {
        return service.products(user.tenantId(), from, to, platformId);
    }
}
