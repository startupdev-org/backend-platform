package com.platform.utils;

import com.platform.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PageRequestsTest {

    private static final Set<String> SORTABLE = Set.of("name", "createdAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private PageRequest build(int page, int size, String sort) {
        return PageRequests.of(page, size, sort, SORTABLE, DEFAULT_SORT);
    }

    @Test
    void appliesDefaultSortWhenNoSortRequested() {
        Sort sort = build(0, 10, null).getSort();

        assertEquals(Sort.Direction.DESC, sort.getOrderFor("createdAt").getDirection());
        assertNotNull(sort.getOrderFor("id"));
    }

    @Test
    void blankSortIsTreatedAsNoSort() {
        assertEquals(build(0, 10, null).getSort(), build(0, 10, "   ").getSort());
    }

    @Test
    void parsesFieldAndDirection() {
        Sort sort = build(0, 10, "name,desc").getSort();

        assertEquals(Sort.Direction.DESC, sort.getOrderFor("name").getDirection());
    }

    @Test
    void defaultsToAscendingWhenDirectionOmitted() {
        Sort sort = build(0, 10, "name").getSort();

        assertEquals(Sort.Direction.ASC, sort.getOrderFor("name").getDirection());
    }

    @Test
    void appendsIdTiebreakerSoPagingIsDeterministic() {
        Sort sort = build(0, 10, "name,asc").getSort();

        assertEquals(2, sort.stream().count());
        assertEquals("id", sort.stream().toList().get(1).getProperty());
    }

    @Test
    void doesNotDuplicateIdTiebreaker() {
        Sort sort = PageRequests.of(0, 10, null, Set.of("id"), Sort.by("id")).getSort();

        assertEquals(1, sort.stream().count());
    }

    @Test
    void rejectsFieldOutsideTheWhitelist() {
        BadRequestException ex = assertThrows(BadRequestException.class, () -> build(0, 10, "password"));

        assertTrue(ex.getMessage().contains("password"));
    }

    @Test
    void rejectsUnknownDirection() {
        assertThrows(BadRequestException.class, () -> build(0, 10, "name,sideways"));
    }

    @Test
    void rejectsMalformedSortExpression() {
        assertThrows(BadRequestException.class, () -> build(0, 10, "name,asc,extra"));
    }

    @Test
    void capsPageSize() {
        assertEquals(PageRequests.MAX_PAGE_SIZE, build(0, 100_000, null).getPageSize());
    }

    @Test
    void keepsSizesUnderTheCap() {
        assertEquals(25, build(0, 25, null).getPageSize());
    }

    @Test
    void rejectsNegativePageAndNonPositiveSize() {
        assertThrows(BadRequestException.class, () -> build(-1, 10, null));
        assertThrows(BadRequestException.class, () -> build(0, 0, null));
    }
}
