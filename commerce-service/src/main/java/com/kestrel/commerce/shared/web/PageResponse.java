package com.kestrel.commerce.shared.web;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Stable JSON representation of a page of results.
 *
 * <p>We deliberately do not serialise Spring's {@code Page} directly: its JSON shape is an implementation detail that
 * changed between Spring versions, and our API contract must not change because we upgraded a library.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<? super E, ? extends T> mapper) {
        List<T> content = page.getContent().stream().<T>map(mapper).toList();
        return new PageResponse<>(
                content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
