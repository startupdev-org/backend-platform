package com.platform.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The single highest-value test in BP-61: boots the <b>full</b> Spring context
 * against a real, freshly-migrated Postgres.
 *
 * <p>This alone catches two classes of bug that the Mockito unit tests structurally
 * cannot, because none of them start a Spring context:
 *
 * <ul>
 *   <li>Broken bean wiring - a missing dependency, a circular reference, a
 *       misconfigured {@code @ConditionalOnProperty} - that previously would not
 *       surface until the app failed to boot on Render.</li>
 *   <li>Entity/migration drift - {@code spring.jpa.hibernate.ddl-auto: validate}
 *       (see application.yml, inherited unchanged by application-test.yml) makes
 *       Hibernate compare every {@code @Entity} mapping against the schema that
 *       Flyway just built from {@code V1__baseline_schema.sql} through the highest
 *       versioned migration on the classpath, and refuses to start the context if
 *       they disagree. Context startup failing here is exactly the signal this
 *       ticket exists to produce, in CI, instead of at deploy time.</li>
 * </ul>
 *
 * <p>Flyway itself also refuses to start if two migration files share a version
 * (the BP-140 collision), so simply reaching the assertions below proves the
 * migration set is internally consistent. {@link com.platform.migration.MigrationVersioningTest}
 * guards that statically, without a container, so a duplicate is caught at
 * {@code mvn test} time rather than only here.
 */
@SpringBootTest
class ApplicationContextIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private DataSource dataSource;

    /**
     * If this method runs at all, the context already loaded successfully - which
     * means Flyway ran every migration against the container and Hibernate's
     * {@code ddl-auto: validate} accepted the result. The body adds concrete,
     * positive assertions on top of "did not throw": every migration is recorded
     * as applied and successful, and the applied versions form a gap-free
     * {@code 1..N} sequence with no duplicates.
     */
    @Test
    void contextLoadsAndEveryMigrationAppliesCleanly() {
        assertNotNull(context);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT version, success FROM flyway_schema_history "
                        + "WHERE type != 'BASELINE' ORDER BY installed_rank");

        assertFalse(rows.isEmpty(), "expected at least one versioned migration to have been applied");

        assertTrue(
                rows.stream().allMatch(r -> Boolean.TRUE.equals(r.get("success"))),
                "every migration must be recorded as successful");

        List<Integer> versions = rows.stream()
                .map(r -> Integer.parseInt((String) r.get("version")))
                .toList();

        // Applied in ascending order, no duplicates, no gaps: exactly 1..N.
        List<Integer> expected = java.util.stream.IntStream.rangeClosed(1, versions.size())
                .boxed()
                .toList();
        assertEquals(
                expected,
                versions,
                "expected the applied migrations to be a gap-free 1.." + versions.size()
                        + " sequence, in order, with no duplicate versions");
    }
}
