# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build
mvn clean package

# Run (dev profile)
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"

# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=BusinessServiceTest

# Run a single test method
mvn test -Dtest=BusinessServiceTest#createBusiness_success

# Build without tests (for CI/Docker)
mvn clean package -DskipTests
```

## Architecture

Spring Boot 3.2 / Java 17 REST API. Package root: `com.platform`.

**Standard layering**: `controller` → `service` → `repository` (Spring Data JPA) → PostgreSQL.

**Auth flow**: `JwtAuthenticationFilter` intercepts every request, extracts the Bearer token via `JwtUtils`, and sets a `UsernamePasswordAuthenticationToken` in the `SecurityContextHolder`. The principal stored is the user's **email**. Services retrieve the current user by calling `SecurityContextHolder → authentication.getPrincipal() → userService.getUserByUsername(email)`.

**Two roles**: `BUSINESS_ADMIN` (creates and manages businesses) and `PLATFORM_ADMIN` (full access). Role is embedded in the JWT and loaded as `ROLE_<role>` authority — no `UserDetailsService` is used.

**Key domain relationships**:
- `User` owns one or more `Business` entities (lazy `@ManyToOne owner`).
- `Business` has `Employee`s, `ProvidedService`s, `Location`s, `BusinessWorkingHours`, and `BusinessFeature`s (all `@OneToMany`).
- `EmployeeLocationServicePrice` is a three-way join (`Employee` × `ProvidedService` × `Location`) with a unique constraint — used to define per-employee, per-location pricing.
- `Booking` ties a customer to a business, employee, and service.
- `Review` is linked to a `Booking`.

**DTO mapping**: `BusinessMapper` (manual static methods, not MapStruct) handles `Business → BusinessResponseDTO`. Most other DTOs use plain constructors or `@Builder`. MapStruct is in the dependency and annotation processor but is not yet used widely.

**Storage**: `StorageService` integrates with **Supabase Storage** via `RestTemplate`. It generates a short-lived presigned upload URL (60 s) and returns the permanent public URL. Configured via `supabase.url`, `supabase.service-key`, `supabase.bucket` in `application.yml`.

**Slugs**: `SlugGenerator.generate(name)` produces the unique URL-safe identifier stored on `Business.slug`.

**Time slots**: `TimeSlotGenerator` generates available booking slots based on service duration.

## Configuration

Secrets are loaded from `src/main/resources/secrets.properties` (gitignored). Required keys:

```
DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD
JWT_SECRET   # ≥32 chars
JWT_EXPIRATION  # ms, default 86400000
SUPABASE_URL
SERVICE_ROLE_KEY
ALLOWED_ORIGINS
SERVER_PORT  # default 8080
```

The `dev` profile (`application-dev.yml`) sets `ddl-auto: create-drop` and enables verbose SQL logging.

## Testing

Tests use **Mockito** (`@ExtendWith(MockitoExtension.class)`) — no Spring context, no database. The `SecurityContextHolder` is manually seeded in `@BeforeEach` and cleared in `@AfterEach`. Tests live under `src/test/java/com/platform/`.

## CI / Deployment

Pushing to `dev` triggers `.github/workflows/dev.yml`: Maven build → Docker image pushed to GHCR → Render deploy hook triggered. Production image is built from the `Dockerfile` at the repo root.