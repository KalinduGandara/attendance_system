package com.attendance.common.web;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        String sort) {

    public static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
        List<T> items = page.getContent().stream().map(mapper).toList();
        return new PageResponse<>(items, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), sortToString(page.getSort()));
    }

    public static <T> PageResponse<T> of(Page<T> page) {
        return of(page, Function.identity());
    }

    private static String sortToString(Sort sort) {
        if (sort.isUnsorted()) {
            return null;
        }
        return sort.stream()
                .map(o -> o.getProperty() + "," + (o.isAscending() ? "asc" : "desc"))
                .collect(Collectors.joining(";"));
    }
}
