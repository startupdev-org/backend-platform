# ---- build ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Dependency layer: only re-resolves when the pom changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# ---- runtime ----
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Render mounts secret files (/etc/secrets/*) owned by group 1000; the app user
# must be in that group to read them. The jammy base image has no GID 1000, so
# create it first. usermod needs root, so run it before USER.
#
# eclipse-temurin:17-jre-jammy carries no HTTP client at all - curl and wget are both
# absent - so the HEALTHCHECK below has nothing to run without installing one. curl is
# the smaller, more standard pick for this (BP-66); this all still needs root, so it
# runs in the same layer as the user/group setup, before USER switches away from it.
RUN groupadd --system app \
    && (getent group 1000 >/dev/null || groupadd --gid 1000 rendersecrets) \
    && useradd --system --gid app app \
    && usermod -a -G 1000 app \
    && apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /build/target/*.jar app.jar
USER app

# Render overrides this via the PORT env var.
EXPOSE 8080

# Hits the readiness probe (BP-66), not liveness: readiness is the one wired to the
# DataSource health indicator, so this also catches "the JVM is up but Neon is
# unreachable" rather than just "the JVM is up". start-period gives Flyway migration
# plus context startup room before failures count; $PORT falls back to 8080 for a
# local `docker run` where Render's env var isn't set.
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl -f "http://localhost:${PORT:-8080}/actuator/health/readiness" || exit 1

# MaxRAMPercentage rather than a fixed -Xmx: the JVM sizes the heap off the container's
# cgroup memory limit (container support is on by default since JDK 10) instead of the
# host's, which matters on Render's small instances. 75% leaves the remaining quarter
# for metaspace, thread stacks, direct buffers, and the GC's own overhead - the usual
# headroom figure for a container-sized Spring Boot app rather than a bare heap-only VM.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
