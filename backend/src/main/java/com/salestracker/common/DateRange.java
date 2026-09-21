package com.salestracker.common;

import com.salestracker.auth.ApiException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Inclusive date range turned into a half-open [from, toExclusive) timestamp range,
 * so an order at 23:59 on the last day is included and one at 00:00 the next day is not.
 * A missing bound means "unbounded".
 */
public record DateRange(LocalDateTime from, LocalDateTime toExclusive) {
    private static final LocalDateTime OPEN_START = LocalDate.of(1970, 1, 1).atStartOfDay();
    private static final LocalDateTime OPEN_END = LocalDate.of(2999, 12, 31).atStartOfDay();

    public static DateRange of(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "'from' must not be after 'to'");
        }
        return new DateRange(
                from == null ? OPEN_START : from.atStartOfDay(),
                to == null ? OPEN_END : to.plusDays(1).atStartOfDay());
    }
}
