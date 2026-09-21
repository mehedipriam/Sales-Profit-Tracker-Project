package com.salestracker.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Helpers shared by list endpoints. */
public final class Search {
    private Search() {}

    /** Lower-cased LIKE pattern; empty input matches everything. */
    public static String pattern(String q) {
        return q == null ? "%" : "%" + q.trim().toLowerCase().replace("%", "\\%").replace("_", "\\_") + "%";
    }

    public static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public static Pageable page(int page, int size, String sortBy) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(sortBy));
    }
}
