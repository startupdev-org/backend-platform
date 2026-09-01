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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The single highest-value test in BP-61: boots the <b>full</b> Spring context
 * against a real, freshly-migrated Postgres.
 *
 * <p>This alone catches two classes of bug that 318 Mockito unit tests structurally
 * cannot, because none of them start a Spring context:
 *
 * <ul>
 *   <li>Broken bean wiring - a missing dependency, a circular reference, a
 *       misconfigured {@code @ConditionalOnProperty} - that previously would not
 *       surface until the app failed to boot on Render.</li>
 *   <li>Entity/migration drift - {@code spring.jpa.hibernate.ddl-auto: validate}
 *       (see application.yml, inherited unchanged by application-test.yml) makes
 *       Hibernate compare every {@code @Entity} mapping against the schema that
 *       Flyway just built from {@code V1__baseline_schema.sql} through
 *       {@code V9__add_password_reset_tokens.sql}, and refuses to start the
 *       context if they disagree. Context startup failing here is exactly the
 *       signal this ticket exists to produce, in CI, instead of at deploy time.</li>
 * </ul>
 */
@SpringBootTest
class ApplicationContextIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private DataSource dataSource;

    /**
     * If this method runs at all, the context already loaded successfully - which
     * means Flyway ran V1..V9 against the container and Hibernate's ddl-auto:
     * validate accepted the result. The body adds a concrete, positive assertion
     * on top of "did not throw": every migration is recorded as applied and
     * successful.
     */
    @Test
    void contextLoadsAndAllNineMigrationsApplyCleanly() {
        assertNotNull(context);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT version, success FROM flyway_schema_history "
                        + "WHERE type != 'BASELINE' ORDER BY installed_rank");

        List<String> versions = rows.stream().map(r -> (String) r.get("version")).toList();
        assertEquals(
                List.of("1", "2", "3", "4", "5", "6", "7", "8", "9"),
                versions,
                "expected V1..V9 to have been applied, in order, to the fresh container schema");

        assertTrue(
                rows.stream().allMatch(r -> Boolean.TRUE.equals(r.get("success"))),
                "every migration must be recorded as successful");
    }
}
