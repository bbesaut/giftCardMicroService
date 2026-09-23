package com.finovago.p2p.dto;

import java.util.List;

import org.springframework.data.domain.Page;

import io.swagger.v3.oas.annotations.media.Schema;

// Wraps a Spring Data Page into a stable, explicit API shape instead of serializing Page directly.
// Page's own JSON representation is an internal implementation detail (its exact fields have changed
// across Spring Data versions, and it exposes Pageable/Sort as nested objects we don't want to
// commit to as a public contract) - this is the boundary between "how we query" and "what we return".
@Schema(description = "A page of results, with pagination metadata.")
public record PagedResponse<T>(
    @Schema(description = "The items in this page")
    List<T> content,

    @Schema(description = "Current page number (0-indexed)", example = "0")
    int page,

    @Schema(description = "Number of items per page", example = "20")
    int size,

    @Schema(description = "Total number of items across all pages", example = "137")
    long totalElements,

    @Schema(description = "Total number of pages", example = "7")
    int totalPages
) {
    public static <T> PagedResponse<T> from(Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
