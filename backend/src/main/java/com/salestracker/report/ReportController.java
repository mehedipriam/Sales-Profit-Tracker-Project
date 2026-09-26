package com.salestracker.report;

import com.salestracker.auth.AuthUser;
import com.salestracker.report.ReportService.ProductsResponse;
import com.salestracker.report.ReportService.SummaryResponse;
import com.salestracker.report.ReportService.TrendResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;

/** Owner only (Phase 7b): reports, the CSV export and the monthly statement all read through here. */
@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class ReportController {
    private final ReportService service;
    private final ReportExportService exportService;

    public ReportController(ReportService service, ReportExportService exportService) {
        this.service = service;
        this.exportService = exportService;
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

    /** CSV of every order line in the filtered range (all statuses), for Excel. */
    @GetMapping("/export.csv")
    public void exportCsv(@AuthenticationPrincipal AuthUser user,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                          @RequestParam(required = false) Long platformId,
                          HttpServletResponse response) throws IOException {
        // Validate before touching the response so invalid input is still a clean JSON 400.
        ReportExportService.Filters filters = exportService.filters(user.tenantId(), from, to, platformId);
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"sales-report.csv\"");
        response.setHeader("Cache-Control", "no-store");
        exportService.writeCsv(user.tenantId(), filters, response.getOutputStream());
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
