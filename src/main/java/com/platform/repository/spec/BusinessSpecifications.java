package com.platform.repository.spec;

import com.platform.entity.Business;
import com.platform.entity.BusinessCategoryType;
import org.springframework.data.jpa.domain.Specification;

/**
 * Composable predicates for the business list endpoint.
 *
 * <p>The list used to be an if / else-if chain over three named repository
 * methods, so only one filter ever reached the query: city plus category
 * silently dropped the category, and minRating on its own was ignored
 * altogether. Specifications AND together, so every supplied filter applies and
 * a new one is one method rather than another branch.
 */
public final class BusinessSpecifications {

    private BusinessSpecifications() {}

    /** Case-insensitive substring match, matching the old findByFilters behaviour. */
    public static Specification<Business> cityContains(String city) {
        return (root, query, cb) -> cb.like(
                cb.lower(root.get("city")),
                "%" + city.toLowerCase() + "%");
    }

    public static Specification<Business> ratingAtLeast(Double minRating) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("ratingOverall"), minRating);
    }

    public static Specification<Business> hasCategory(BusinessCategoryType category) {
        return (root, query, cb) -> cb.equal(root.get("businessCategory"), category);
    }
}
