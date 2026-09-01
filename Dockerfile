# Where the jar comes from. Declared before the first FROM so it is a global ARG
# and can be used in a FROM line below.
#   build    (default) - compile from source inside the image. `docker build .` on a
#                        clean checkout just works, with no prior Maven run. This is
#                        the local path, and why this file stays self-contained.
#   prebuilt           - take the jar the caller already built into target/. CI passes
#                        this so the project is compiled exactly once per run.
# BuildKit only builds the stages the target actually depends on, so the branch that
# is not selected costs nothing and its inputs are never needed. BuildKit is the
# default in Docker 23+ and is always used by buildx / docker/build-push-action.
ARG JAR_SOURCE=build

# ---- build ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Dependency layer: only re-resolves when the pom changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# <finalName>app</finalName> in the pom fixes the output at target/app.jar. Move it
# to /app.jar so both jar sources hand the runtime stage the identical path.
RUN mvn -B clean package -DskipTests && mv target/app.jar /app.jar

# ---- prebuilt ----
# Requires target/app.jar in the build context (run `mvn package` first).
# .dockerignore lets that one file through from target/ and nothing else.
FROM eclipse-temurin:17-jre-jammy AS prebuilt
COPY target/app.jar /app.jar

# ---- jar source ----
# Resolves to whichever of the two stages above JAR_SOURCE names.
FROM ${JAR_SOURCE} AS jar

# ---- runtime ----
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Render mounts secret files (/etc/secrets/*) owned by group 1000; the app user
# must be in that group to read them. The jammy base image has no GID 1000, so
# create it first. usermod needs root, so run it before USER.
RUN groupadd --system app \
    && (getent group 1000 >/dev/null || groupadd --gid 1000 rendersecrets) \
    && useradd --system --gid app app \
    && usermod -a -G 1000 app
COPY --from=jar /app.jar app.jar
USER app

# Render overrides this via the PORT env var.
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
