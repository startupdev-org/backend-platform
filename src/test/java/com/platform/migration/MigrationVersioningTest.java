package com.platform.migration;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Static guard on the Flyway migration set - no Spring context, no Testcontainers,
 * so it runs on every {@code mvn test} and fails in milliseconds.
 *
 * <p>Exists because of BP-140: two branches were cut from {@code dev} in parallel,
 * each added a {@code V10__*.sql}, and Git merged both with no conflict (different
 * filenames). Flyway then refused to start with
 * {@code "Found more than one migration with version '10'"}, which only surfaced
 * in the container-backed {@link com.platform.integration.ApplicationContextIntegrationTest}
 * after both had already landed on {@code dev}. This test catches that class of
 * mistake at PR-review time instead.
 */
class MigrationVersioningTest {

    private static final Pattern VERSIONED = Pattern.compile("^V(\\d+)__.+\\.sql$");

    @Test
    void migrationVersionsAreUniqueAndGapFree() {
        List<File> scripts = versionedMigrationScripts();
        assertFalse(scripts.isEmpty(), "no V*__*.sql migrations found on the classpath");

        List<Integer> versions = new ArrayList<>();
        for (File f : scripts) {
            Matcher m = VERSIONED.matcher(f.getName());
            if (m.matches()) {
                versions.add(Integer.parseInt(m.group(1)));
            }
        }
        versions.sort(Comparator.naturalOrder());

        // No two files share a version prefix.
        long distinct = versions.stream().distinct().count();
        assertEquals(
                versions.size(),
                distinct,
                "duplicate Flyway migration version(s): " + duplicates(versions)
                        + " - renumber one of the colliding files");

        // Versions form a gap-free 1..N sequence.
        List<Integer> expected = IntStream.rangeClosed(1, versions.size()).boxed().toList();
        assertEquals(
                expected,
                versions,
                "Flyway migration versions must be a gap-free 1.." + versions.size()
                        + " sequence; found " + versions);
    }

    private static List<Integer> duplicates(List<Integer> versions) {
        List<Integer> seen = new ArrayList<>();
        List<Integer> dupes = new ArrayList<>();
        for (Integer v : versions) {
            if (seen.contains(v)) {
                dupes.add(v);
            } else {
                seen.add(v);
            }
        }
        return dupes;
    }

    private static List<File> versionedMigrationScripts() {
        URL dir = Thread.currentThread().getContextClassLoader().getResource("db/migration");
        assertNotNull(dir, "db/migration is not on the test classpath");
        File[] files = new File(dir.getFile()).listFiles((d, name) -> name.endsWith(".sql"));
        assertNotNull(files, "db/migration did not resolve to a readable directory: " + dir);
        return Arrays.stream(files)
                .filter(f -> VERSIONED.matcher(f.getName()).matches())
                .sorted(Comparator.comparing(File::getName))
                .toList();
    }
}
