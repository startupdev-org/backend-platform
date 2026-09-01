package com.platform.repository.spec;

import com.platform.entity.ProvidedService;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;
import java.util.UUID;

/**
 * Composable predicates for the service list endpoints (BP-12).
 *
 * <p>Same reasoning as {@link BusinessSpecifications}: the business scope and the optional
 * {@code q} search have to land in the same query rather than in a chain of named repository
 * methods, or a future filter could be bolted on without the business-id predicate that
 * actually keeps the query tenant-scoped. Specifications AND together at the call site
 * ({@code ProvidedServicesService}), so the scope is never something a caller can forget to
 * apply.
 */
public final class ServiceSpecifications {

    private ServiceSpecifications() {}

    /** Escape character used to neutralise LIKE wildcards in user input; see below. */
    private static final char ESCAPE_CHAR = '\\';

    public static Specification<ProvidedService> belongsToBusiness(UUID businessId) {
        return (root, query, cb) -> cb.equal(root.get("business").get("id"), businessId);
    }

    public static Specification<ProvidedService> isActive(boolean active) {
        return (root, query, cb) -> cb.equal(root.get("active"), active);
    }

    /**
     * Case-insensitive substring match against {@code name} OR {@code description}.
     *
     * <p>{@code description} is nullable. That is not a problem here: {@code cb.lower(...)}
     * and {@code cb.like(...)} only build the SQL expression, they do not evaluate it, and
     * {@code LOWER(NULL) LIKE '...'} evaluates to {@code NULL} (falsy) at query time rather
     * than throwing - so a service with no description just never matches on that side of
     * the OR, exactly as if it had been excluded explicitly.
     *
     * <p>{@code toLowerCase(Locale.ROOT)} is deliberate: the platform-default locale can be
     * Turkish, where {@code "I".toLowerCase()} yields "ı" (dotless) instead of "i" and would
     * make an "i" search silently miss services stored with an "I" - the same trap
     * {@code SlugGeneratorTest} pins for {@link com.platform.utils.SlugGenerator}.
     *
     * <p>{@code %} and {@code _} are SQL LIKE wildcards. Left unescaped, searching for a bare
     * {@code %} (or a name/description fragment that happens to contain one) would match
     * every row instead of the literal character, so both wildcards - and the escape
     * character itself, in case it ever appears in a query - are escaped in the caller's
     * input before it is wrapped in {@code %...%}.
     */
    public static Specification<ProvidedService> nameOrDescriptionContains(String q) {
        String pattern = "%" + escapeLikeWildcards(q.toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), pattern, ESCAPE_CHAR),
                cb.like(cb.lower(root.get("description")), pattern, ESCAPE_CHAR));
    }

    /** Escapes the escape character first so it cannot collide with the wildcards it introduces. */
    private static String escapeLikeWildcards(String value) {
        return value
                .replace(String.valueOf(ESCAPE_CHAR), String.valueOf(ESCAPE_CHAR) + ESCAPE_CHAR)
                .replace("%", ESCAPE_CHAR + "%")
                .replace("_", ESCAPE_CHAR + "_");
    }
}
