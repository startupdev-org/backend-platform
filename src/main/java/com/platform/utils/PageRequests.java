package com.platform.utils;

import com.platform.exception.BadRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.Set;
import java.util.TreeSet;

/**
 * Builds a {@link PageRequest} from the raw {@code page} / {@code size} / {@code sort}
 * request parameters.
 *
 * <p>Two things this guards that a bare {@code PageRequest.of(page, size)} does not:
 * a hard cap on {@code size}, so {@code ?size=100000} cannot be used to dump a whole
 * table in one request, and a whitelist on the sort field, so an unknown property
 * never reaches the query (Spring Data would raise a {@code PropertyReferenceException}
 * and the caller would see a 500 instead of a 400).
 */
public final class PageRequests {

    public static final int MAX_PAGE_SIZE = 100;

    private PageRequests() {
    }

    public static PageRequest of(int page, int size, String sort, Set<String> sortableFields, Sort defaultSort) {
        if (page < 0) {
            throw new BadRequestException("page must be 0 or greater");
        }
        if (size < 1) {
            throw new BadRequestException("size must be 1 or greater");
        }

        return PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), resolveSort(sort, sortableFields, defaultSort));
    }

    /** Accepts {@code "name"} or {@code "name,desc"}; blank falls back to the caller's default. */
    private static Sort resolveSort(String sort, Set<String> sortableFields, Sort defaultSort) {
        if (sort == null || sort.isBlank()) {
            return withTiebreaker(defaultSort);
        }

        String[] parts = sort.split(",");
        if (parts.length > 2) {
            throw new BadRequestException("sort must be 'field' or 'field,asc|desc'");
        }

        String field = parts[0].trim();
        if (!sortableFields.contains(field)) {
            throw new BadRequestException("Cannot sort by '" + field + "'. Sortable fields: "
                    + String.join(", ", new TreeSet<>(sortableFields)));
        }

        Sort.Direction direction = parts.length == 2
                ? Sort.Direction.fromOptionalString(parts[1].trim())
                        .orElseThrow(() -> new BadRequestException("Sort direction must be 'asc' or 'desc'"))
                : Sort.Direction.ASC;

        return withTiebreaker(Sort.by(direction, field));
    }

    /**
     * Sorting on a non-unique column (name, rating, price) leaves rows with equal values
     * in an arbitrary order, which lets the same row show up on two different pages. The
     * id tiebreaker is what makes paging through a list deterministic.
     */
    private static Sort withTiebreaker(Sort sort) {
        boolean alreadyOrdersById = sort.stream().anyMatch(order -> "id".equals(order.getProperty()));
        return alreadyOrdersById ? sort : sort.and(Sort.by(Sort.Direction.ASC, "id"));
    }
}
