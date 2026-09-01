package com.platform.repository.spec;

import com.platform.entity.ProvidedService;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Direct unit tests of {@link ServiceSpecifications} (BP-12), evaluating each
 * {@code Specification} against mocked JPA Criteria types the same way
 * {@code BusinessServiceTest.evaluateCapturedSpecification} does - no Spring context, no
 * database, just checking which {@code CriteriaBuilder} calls each predicate builds.
 */
class ServiceSpecificationsTest {

    @SuppressWarnings("unchecked")
    private Root<ProvidedService> mockRoot() {
        return mock(Root.class, RETURNS_DEEP_STUBS);
    }

    @Test
    void belongsToBusiness_matchesTheBusinessIdColumn() {
        UUID businessId = UUID.randomUUID();
        Root<ProvidedService> root = mockRoot();
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);

        Path<Object> businessIdPath = mock(Path.class);
        when(root.get("business").get("id")).thenReturn(businessIdPath);

        ServiceSpecifications.belongsToBusiness(businessId).toPredicate(root, (CriteriaQuery<?>) query, cb);

        verify(cb).equal(businessIdPath, businessId);
    }

    @Test
    void isActive_matchesTheActiveColumn() {
        Root<ProvidedService> root = mockRoot();
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);

        Path<Object> activePath = mock(Path.class);
        when(root.get("active")).thenReturn(activePath);

        ServiceSpecifications.isActive(true).toPredicate(root, (CriteriaQuery<?>) query, cb);

        verify(cb).equal(activePath, true);
    }

    @Test
    void nameOrDescriptionContains_orsTheTwoColumnsCaseInsensitively() {
        Root<ProvidedService> root = mockRoot();
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);

        ServiceSpecifications.nameOrDescriptionContains("Cut").toPredicate(root, (CriteriaQuery<?>) query, cb);

        verify(root).get("name");
        verify(root).get("description");
        // Both sides go through cb.lower(...) before the LIKE, i.e. case-insensitive.
        verify(cb, org.mockito.Mockito.times(2)).lower(any());
        verify(cb, org.mockito.Mockito.times(2)).like(any(), eq("%cut%"), eq('\\'));
        verify(cb).or(any(), any());
    }

    /**
     * A null {@code description} is not a special case the specification has to branch on:
     * {@code cb.lower}/{@code cb.like} only build the SQL expression tree here, they never
     * touch an actual value, so there is nothing for this predicate to null-check. At query
     * time {@code LOWER(NULL) LIKE '...'} evaluates to NULL (falsy), so a null description
     * simply fails to match rather than throwing - this test documents that no special
     * handling is needed, it exercises the same code path as any other description.
     */
    @Test
    void nameOrDescriptionContains_buildsTheSameExpressionRegardlessOfNullability() {
        Root<ProvidedService> root = mockRoot();
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);

        ServiceSpecifications.nameOrDescriptionContains("cut")
                .toPredicate(root, (CriteriaQuery<?>) query, cb);

        // No exception, and the description column was still queried - the null check
        // happens in SQL (LOWER(NULL) IS NULL), not in Java.
        verify(root).get("description");
    }

    @Test
    void nameOrDescriptionContains_lowercasesUsingLocaleRoot_notThePlatformDefault() {
        Locale previous = Locale.getDefault();
        try {
            // The Turkish locale trap: "I".toLowerCase() yields "ı" (dotless i)
            // under Locale("tr"), not "i". SlugGeneratorTest pins the same bug for
            // SlugGenerator; Locale.ROOT is what keeps this predicate immune to it.
            Locale.setDefault(Locale.forLanguageTag("tr"));

            Root<ProvidedService> root = mockRoot();
            CriteriaQuery<?> query = mock(CriteriaQuery.class);
            CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);

            ServiceSpecifications.nameOrDescriptionContains("HAIRCUT")
                    .toPredicate(root, (CriteriaQuery<?>) query, cb);

            ArgumentCaptor<String> pattern = ArgumentCaptor.forClass(String.class);
            verify(cb, org.mockito.Mockito.atLeastOnce()).like(any(), pattern.capture(), eq('\\'));
            assertEquals("%haircut%", pattern.getValue());
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void nameOrDescriptionContains_escapesPercentSoItDoesNotMatchEverything() {
        Root<ProvidedService> root = mockRoot();
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);

        ServiceSpecifications.nameOrDescriptionContains("%").toPredicate(root, (CriteriaQuery<?>) query, cb);

        ArgumentCaptor<String> pattern = ArgumentCaptor.forClass(String.class);
        verify(cb, org.mockito.Mockito.atLeastOnce()).like(any(), pattern.capture(), eq('\\'));
        // The literal "%" is escaped, so the pattern is "%\%%" - "contains a literal percent",
        // not "match anything".
        assertEquals("%\\%%", pattern.getValue());
    }

    @Test
    void nameOrDescriptionContains_escapesUnderscoreAndTheEscapeCharacterItself() {
        Root<ProvidedService> root = mockRoot();
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);

        // Raw input contains all three characters the escaping has to handle: a literal
        // backslash (the escape character itself), a "%", and a "_".
        ServiceSpecifications.nameOrDescriptionContains("50%_off\\deal")
                .toPredicate(root, (CriteriaQuery<?>) query, cb);

        ArgumentCaptor<String> pattern = ArgumentCaptor.forClass(String.class);
        verify(cb, org.mockito.Mockito.atLeastOnce()).like(any(), pattern.capture(), eq('\\'));
        // Backslash doubled, "%" and "_" each prefixed with a backslash, then wrapped in
        // the substring-match "%...%". As a Java literal: \ -> \\, so this reads noisier
        // than the actual pattern string, which is: %50\%\_off\\deal%
        assertEquals("%50\\%\\_off\\\\deal%", pattern.getValue());
    }
}
