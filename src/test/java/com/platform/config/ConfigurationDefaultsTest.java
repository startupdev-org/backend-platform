package com.platform.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the "a fresh clone will not start" class of bug (BP-72).
 *
 * <p>These are plain text assertions over {@code application.yml}, not a Spring
 * context: the suite has no database, and the whole point of the ticket is that
 * the failure happens <em>before</em> a context can start, as an unresolvable
 * placeholder. A context test would need the very configuration this test exists
 * to check.
 *
 * <p>The second test is the durable one. Every {@code ${VAR}} written without a
 * default is a value the application refuses to boot without, and the only place
 * a newcomer is told about those is {@code secrets.properties.example}. Adding an
 * undefaulted placeholder and forgetting the example file is exactly the drift
 * that made the documented setup wrong for months, so it fails the build now.
 */
class ConfigurationDefaultsTest {

    /** Matches the start of a placeholder: the name, then ':' (has a default) or '}' (none). */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Z][A-Z0-9_]*)([:}])");

    private static String readClasspath(String resource) throws IOException {
        try (InputStream in = ConfigurationDefaultsTest.class.getResourceAsStream(resource)) {
            assertThat(in)
                    .as("%s must be on the classpath - it ships in src/main/resources", resource)
                    .isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Env var names used in application.yml with no ':' default, so startup requires them. */
    private static Set<String> undefaultedPlaceholders(String yaml) {
        Set<String> required = new LinkedHashSet<>();
        Matcher m = PLACEHOLDER.matcher(yaml);
        while (m.find()) {
            if ("}".equals(m.group(2))) {
                required.add(m.group(1));
            }
        }
        return required;
    }

    @Test
    @DisplayName("spring.profiles.active defaults to dev, as CLAUDE.md has always claimed")
    void profilesActiveHasDevDefault() throws IOException {
        String yaml = readClasspath("/application.yml");

        assertThat(yaml)
                .as("Without the default an unset SPRING_PROFILES_ACTIVE is an unresolvable "
                        + "placeholder and the app refuses to start, so the documented local "
                        + "setup does not work. Both Render services set it explicitly and are "
                        + "unaffected either way.")
                .contains("${SPRING_PROFILES_ACTIVE:dev}");

        assertThat(undefaultedPlaceholders(yaml))
                .as("SPRING_PROFILES_ACTIVE must not appear anywhere without a default")
                .doesNotContain("SPRING_PROFILES_ACTIVE");
    }

    @Test
    @DisplayName("every env var required for startup is documented in secrets.properties.example")
    void everyRequiredPlaceholderIsInTheExampleFile() throws IOException {
        Set<String> required = undefaultedPlaceholders(readClasspath("/application.yml"));
        String example = readClasspath("/secrets.properties.example");

        assertThat(required)
                .as("sanity check: application.yml should still have undefaulted placeholders")
                .isNotEmpty();

        for (String key : required) {
            assertThat(example)
                    .as("%s has no default in application.yml, so the app will not start "
                            + "without it - it must be an uncommented key in "
                            + "secrets.properties.example or a fresh clone cannot follow "
                            + "the documented setup", key)
                    .containsPattern("(?m)^" + Pattern.quote(key) + "=");
        }
    }
}
