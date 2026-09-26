package com.finovago.p2p.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.finovago.p2p.dto.PagedResponse;

class PagedResponseUnitTest {

    @Test
    void from_copiesContentAndPaginationMetadata_fromSpringPage() {
        Page<String> page = new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 5);

        PagedResponse<String> result = PagedResponse.from(page);

        assertEquals(List.of("a", "b"), result.content());
        assertEquals(1, result.page());
        assertEquals(2, result.size());
        assertEquals(5, result.totalElements());
        assertEquals(3, result.totalPages()); // ceil(5/2)
    }
}
