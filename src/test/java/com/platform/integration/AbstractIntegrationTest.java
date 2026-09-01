package com.platform.integration;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for every test in this module that needs a real Postgres (BP-61) -
 * the {@code @SpringBootTest} context-smoke test and every {@code @DataJpaTest}
 * repository test.
 *
 * <h2>Container lifecycle: one JVM-wide singleton, not one per test class</h2>
 *
 * The container is started exactly once, in the static initializer below, and is
 * never explicitly stopped. It is deliberately <b>not</b> wired up with the usual
 * {@code @Testcontainers}/{@code @Container} JUnit 5 extension - that pairing
 * starts a fresh container per test class by default, and Postgres startup
 * (~1-2s) times that by however many repository test classes exist. Since
 * {@code POSTGRES} is a {@code static} field on this shared superclass, every
 * subclass across the whole Surefire run sees the same already-running instance:
 * Maven Surefire forks one JVM for the entire {@code mvn test} invocation by
 * default (forkCount=1, reuseForks=true), so the static initializer runs once,
 * on first class load, for the whole suite. Testcontainers' Ryuk sidecar removes
 * the container when that JVM exits, so nothing leaks between runs.
 *
 * <p>{@link ServiceConnection @ServiceConnection} on the field is what makes this
 * idiomatic for Spring Boot 3.1+: it registers a {@code JdbcConnectionDetails}
 * bean derived from the running container, which {@code DataSourceAutoConfiguration}
 * (and, through it, Flyway - see application-test.yml) prefers over
 * {@code spring.datasource.*} entirely. No datasource URL is hand-written anywhere
 * in this test tree.
 *
 * <p>{@code @ActiveProfiles("test")} lives once on this base class - Spring's
 * TestContext framework merges it up the class hierarchy the same way it does
 * {@code @ContextConfiguration}, so every subclass picks up the "test" profile
 * (and therefore application-test.yml) without re-declaring it.
 */
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
        POSTGRES.start();
    }
}
