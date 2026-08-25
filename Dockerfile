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

RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /build/target/*.jar app.jar
USER app

# Render overrides this via the PORT env var.
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
