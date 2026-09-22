package com.salestracker.common;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(List<T> content, int page, int totalPages, long totalElements) {
    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getTotalPages(), page.getTotalElements());
    }

    /** Same page, contents transformed - e.g. redacting fields a Staff caller shouldn't see (Phase 7b). */
    public <R> PageResponse<R> map(Function<T, R> mapper) {
        return new PageResponse<>(content.stream().map(mapper).toList(), page, totalPages, totalElements);
    }
}
