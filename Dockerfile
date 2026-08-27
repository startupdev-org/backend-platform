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
RUN groupadd --system app \
    && (getent group 1000 >/dev/null || groupadd --gid 1000 rendersecrets) \
    && useradd --system --gid app app \
    && usermod -a -G 1000 app
COPY --from=build /build/target/*.jar app.jar
USER app

# Render overrides this via the PORT env var.
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
