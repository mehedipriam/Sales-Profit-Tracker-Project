package com.salestracker.report;

import com.salestracker.auth.AuthUser;
import com.salestracker.report.ReportService.SummaryResponse;
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
}
