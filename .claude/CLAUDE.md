# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

A vertical-agnostic SaaS booking platform for service-based businesses in Moldova — not limited to any single industry. Business owners sign up, build a public profile, and manage their services, employees, and locations; customers book appointments through that public page without needing an account. The platform does not intermediate payments between customers and businesses — customers pay directly, off-platform — so its revenue comes from subscription plans gating business and employee counts, not from transaction fees. Priority is a working, architecturally sound core product first; subscriptions and monetization follow once real usage informs the limits.

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

**Passwords**: `PasswordService` owns every route that changes one. `POST /api/auth/change-password` requires the current password, so a stolen access token alone cannot take an account over; `POST /api/auth/forgot-password` and `POST /api/auth/reset-password` are the recovery pair for someone who cannot log in at all. Reset tokens mirror refresh tokens exactly — 256 bits of `SecureRandom`, stored only as a SHA-256 hex digest in `password_reset_tokens`, single-use, 30 minutes by default (`password-reset.expiration`), swept nightly by `PasswordResetTokenCleanupJob`. `forgot-password` always answers 202 whether or not the address has an account — answering differently would make it a free membership check against the whole user table, the same reasoning login already applies. Every password change, by either route, clears the login lockout, invalidates outstanding reset links, and revokes every refresh token.

**Reset email delivery is a stub.** There is no mail provider on the classpath. `LoggingPasswordResetMailer` writes the reset link to the log at INFO, and `com.platform` logs at INFO in prod — so the link is readable by anyone who can read the logs. The `PasswordResetMailer` interface is the seam a real sender drops into, the same shape as `StorageProvider`. Until BP-136 lands, password reset is a support-desk tool rather than a self-service one, and the frontend entry point should stay closed (BP-94).

**Two roles**: `BUSINESS_ADMIN` (creates and manages businesses) and `PLATFORM_ADMIN` (full access). Role is embedded in the JWT and loaded as `ROLE_<role>` authority — no `UserDetailsService` is used. `@EnableMethodSecurity` is active, so `@PreAuthorize` annotations on controllers are supported in addition to the URL-level rules in `SecurityConfig`.

**Key domain relationships**:
- `User` owns one or more `Business` entities (lazy `@ManyToOne owner`).
- `Business` has `Employee`s, `ProvidedService`s, `Location`s, `BusinessWorkingHours`, and `BusinessFeature`s (all `@OneToMany`).
- `EmployeeLocationServicePrice` is a three-way join (`Employee` × `ProvidedService` × `Location`) with a unique constraint — used to define per-employee, per-location pricing.
- `Booking` ties a customer to a business, employee, and service.
- `Review` is linked to a `Booking`.

**Ownership enforcement**: Services call `business.isNotOwner(currentUser)` and throw `BusinessOwnershipException` (→ 403) to guard mutations. Always check ownership before modifying any resource belonging to a `Business`.

**Exception semantics**: `ResourceNotFoundException` → 404, `BusinessException` → 403, `BusinessFeatureAlreadyExistsException` → 409. `DataIntegrityViolationException` from JPA is caught globally and also maps to 409. `GlobalExceptionHandler` in `com.platform.exception` handles all of these centrally — don't catch and re-throw them in services.

**DTO mapping**: `BusinessMapper` (manual static methods, not MapStruct) handles `Business → BusinessResponseDTO`. Most other DTOs use plain constructors or `@Builder`. MapStruct is in the dependency and annotation processor but is not yet used widely.

**Storage / images**: presigned direct-to-bucket uploads, in `com.platform.storage`. `StorageProvider` is the only abstraction that knows the provider; `R2StorageProvider` (Cloudflare R2, via its S3-compatible API — `software.amazon.awssdk:s3`/`s3-presigner`) is the sole implementation, selected by `storage.provider`. **No image bytes ever pass through this application** — the browser PUTs straight to the bucket.

Three steps: `POST /api/business/{id}/images/upload-url?target=LOGO|COVER` returns a short-lived signed URL plus a **server-generated** `storageKey`; the browser PUTs the file to that URL; `PUT /api/business/{id}/images?target=…` attaches the key. Employee photos mirror this under `/api/business/{id}/employee/{employeeId}/images`. `DELETE` on either path clears the image.

The client never supplies a file name or folder — only a content type (`image/jpeg|png|webp`). `ImageKeys` builds every key as `business/{businessId}/{slot}/{uuid}.{ext}`, and the attach step calls `ImageKeys.requirePrefix` to reject any key that this business and slot would not have produced. That prefix check is the tenant boundary; without it a caller could attach another business's object to their own row.

`logo_key` / `cover_image_key` / `photo_key` (renamed from `*_url` in `V6`) hold the **storage key**, not a URL. `ImageUrlResolver.toPublicUrl` builds the public URL at response time, and passes any value already starting with `http` straight through — that is how pre-V6 rows keep rendering without a data backfill. Response DTO field names are unchanged (`logoUrl`, `coverImageUrl`, `photoUrl`), so the frontend contract did not move.

Images are **not** settable through `BusinessRequestDTO` / `EmployeeRequestDTO` — those fields are gone. Only the image endpoints write them.

The upload size limit **cannot be enforced in Java** in this flow. `storage.max-upload-bytes` is advisory to the client plus a backstop re-check at attach time; the real gate is the bucket's own upload restrictions, and the bucket's CORS rules must allow `PUT` from `ALLOWED_ORIGINS` or every browser upload fails.

Public URLs are built by `R2StorageProvider.toPublicUrl` as `storage.r2.public-url-base + "/" + key` — currently the bucket's `pub-<hash>.r2.dev` subdomain, until a custom domain replaces it (a one-line config change, not a code change).

**Slugs**: `SlugGenerator.generate(name)` produces the unique URL-safe identifier stored on `Business.slug`.

**Time slots**: `TimeSlotGenerator.generateAvailableSlots(day, durationMinutes, open, close)` turns one opening interval into a 30-minute grid of start times where the whole service still fits before `close`. It knows nothing about working-hours rows or bookings. `AvailabilityService` is the caller: it feeds each `BusinessWorkingHours` interval for the requested weekday through the generator, drops slots taken by a non-cancelled `Booking` and slots in the past, and serves `GET /api/business/{businessId}/employee/{employeeId}/availability?date=&serviceId=` (public — the booking page needs it without a login). A weekday with no working-hours row returns an empty list, not a default grid.

**Enums**: `User.UserRole` is an inner enum on `User`. `Booking.BookingStatus` is an inner enum on `Booking`. `ServiceDeliveryType` and `BusinessCategoryType` are top-level enums stored as `EnumType.STRING` in the DB.

## Planned: Subscriptions & Access Control (not yet implemented)

Design decided in architecture planning, not yet built — none of the classes below exist in the codebase yet. Don't assume `Plan`, `Subscription`, or `PaymentEventLog` exist when reading or generating code; this section is a target, not current state.

**Model**: `Plan` (catalog: code, price, billing interval, `maxBusinesses`, `maxEmployeesPerBusiness`, feature flags) and `Subscription` (one per `User`, never per `Business` — the limit is a property of the paying account, not of any single business). `Subscription` holds `status` (`FREE_FOREVER` / `ACTIVE` / `PAST_DUE`), the current billing period, and exactly two external references — processor customer id and subscription id. Keep the payment-processor coupling confined to those two fields; nothing else should know which processor is in use. A `PaymentEventLog` (unique external event id) makes webhook handling idempotent — processors deliver at-least-once, not exactly-once.

**Access states**:
- `FREE_FOREVER` — permanent plan, small limits (1 business, 1 location, employee count still TBD). Hitting the quota blocks only *creation* of new resources; everything already there stays fully usable.
- `ACTIVE` — paid plan, full access within the plan's limits.
- `PAST_DUE` — payment failed on a paid plan. Does **not** silently fall back to `FREE_FOREVER` — stays attached to its real plan, just blocked, until payment resolves. Blocks: creating new resources, and any premium `BusinessFeature`s tied to the plan. Stays open: reading existing data, managing existing bookings — and critically, **the public booking page stays live**, so the business's own customers are never affected by the owner's billing issue.

**Enforcement point**: a limit check (`User → active Subscription → Plan → count vs. limit`) must run at the top of every create path (`createBusiness`, `createEmployee`, `createLocation`, …), before any write. This can't live in `SecurityConfig` URL matchers — those are static role rules, this is a dynamic count comparison.

**Registration stays self-serve**: `POST /api/auth/register` stays public (product-led-growth pattern, no admin-gated account creation). New accounts land on `FREE_FOREVER` automatically — every user always has an active plan, no null-plan branches anywhere.

**Tier limits (draft, numbers still being tuned)**:

| Plan | Businesses | Employees | Locations | Services |
|---|---|---|---|---|
| Free Forever | 1 | 1–3 (TBD) | 1 | unlimited |
| Solo (paid) | 1 | 5 | unlimited | unlimited |
| Business (paid) | 1 | 10 (+ per-seat cost above) | unlimited | unlimited |
| Multi-Business (paid) | 5 (negotiable) | unlimited, per-seat cost | unlimited | unlimited |

Locations and services are intentionally unlimited on every paid tier — they're near-free to host and don't correlate with cost or usage. Businesses and employees are the two dimensions that actually track cost/value, so those are the only ones gated.

**Payment processor**: undecided. Stripe does not support a direct merchant account for a Moldova-registered entity (not in Stripe's supported-country list) — Paddle or Lemon Squeezy (merchant-of-record model, they own VAT/tax compliance) is the practical route unless/until a foreign entity is set up for a direct Stripe account.

**Known gaps to close before or alongside this work**:
- Working hours are unprotected. `BusinessWorkingHoursController` is mapped at `/api/businesses/{businessId}/working-hours` (plural) while every `SecurityConfig` rule — including the `/api/business/**` catch-all — uses the singular path, so the `hasRole(BUSINESS_ADMIN)` rules at `SecurityConfig:78-80` are dead and these endpoints fall through to `.anyRequest().authenticated()`. `BusinessWorkingHoursService` also has no ownership check on `create`/`update`/`delete`, and the controller never resolves the current user. Net effect: any authenticated account, of any role, can create or delete working hours for any business. Fix by mirroring `LocationService.validateBusinessOwnership` and correcting the path (or the matcher).
- Cross-tenant reads on the pricing endpoints. `EmployeeLocationServicePriceService.getById` / `getByEmployee` / `getByEmployeeAndLocation` take no `currentUser` and do no ownership check, and `SecurityConfig` maps `GET /api/business/*/employee-service-price/**` to `.authenticated()` — so any logged-in tenant can read another business's pricing table. Deliberately left open for now; decide whether prices are owner-only (add the ownership check) or public booking-page data (switch the rule to `permitAll()`, like the location GETs) before billing lands.
- `BusinessCategoryType` is a closed enum (`BARBERSHOP, BEAUTY, SPA, NAILS`). Product direction is now general services, not beauty-only — this needs to become an admin-managed lookup table (id/slug/name), not a hardcoded enum, or every new vertical requires a code deploy.
- Orphaned uploads are never reclaimed. An upload URL that is issued but never attached leaves an object in the bucket forever. The 60s signed-URL TTL keeps the window small and keys are namespaced per business so a sweep is easy, but there is no reaper yet — objects older than 24h whose key appears in no row should be deleted on a schedule.
- `JwtAuthenticationFilter` never re-checks the user against the DB per request — role and `isEnabled` are trusted from the JWT claims for the token's full lifetime (up to `JWT_EXPIRATION`). This needs to change for `PAST_DUE` enforcement to actually cut access in real time rather than only at the next login.

## Configuration

Three runtime contexts, two databases:

| Context | Profile | Database | Secrets from |
|---|---|---|---|
| Local machine | `dev` (default) | dev Neon | `src/main/resources/secrets.properties` |
| Render dev service | `dev` | dev Neon | Render env vars |
| Render prod service | `prod` | prod Neon | Render env vars |

`application.yml` holds only environment-agnostic settings; every environment-specific value comes from an env var. `application-dev.yml` adds verbose SQL/security logging, `application-prod.yml` silences logs and disables Swagger. The active profile defaults to `dev` (`spring.profiles.active: ${SPRING_PROFILES_ACTIVE:dev}`) — both Render services set `SPRING_PROFILES_ACTIVE` explicitly.

Locally, copy `src/main/resources/secrets.properties.example` to `secrets.properties` (gitignored) and fill it in. Required keys:

```
# No default in application.yml — the app will not start without these:
DB_URL       # full Neon JDBC string, incl. sslmode/channelBinding
DB_USER, DB_PASSWORD
JWT_SECRET   # ≥32 chars
ALLOWED_ORIGINS
STORAGE_BUCKET  # required even if you never upload an image

# Defaulted — set only to override:
JWT_EXPIRATION             # ms, default 900000 (15 min)
JWT_REFRESH_EXPIRATION     # ms, default 2592000000 (30 days)
PASSWORD_RESET_EXPIRATION  # ms, default 1800000 (30 min)
FRONTEND_BASE_URL          # default http://localhost:5173
SERVER_PORT                # local only; Render supplies PORT, which wins
R2_ACCOUNT_ID, R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY, R2_PUBLIC_URL_BASE
                           # default empty; every image upload fails without them
```

**Schema is owned by Flyway**, never by Hibernate. `ddl-auto` is `validate` in every profile and must stay that way — it is the guard that fails startup when the entities and the migrations drift apart. Migrations live in `src/main/resources/db/migration` as `V<n>__description.sql`; `V1__baseline_schema.sql` was generated from the entity mappings. `baseline-on-migrate: true` is set because both Neon databases predate Flyway and already carry a schema. Every entity change now ships with its own migration in the same commit.

## Testing

Tests use **Mockito** (`@ExtendWith(MockitoExtension.class)`) — no Spring context, no database. The `SecurityContextHolder` is manually seeded in `@BeforeEach` and cleared in `@AfterEach`. Tests live under `src/test/java/com/platform/`.

## Git & GitHub workflow
Every task gets its own branch, own commits and own messages with this. It is important everything to be organized and with clear messages 

### Never commit directly to main.

Branch naming: feat/short-description, fix/short-description, chore/short-description

When a task is finished, or when Marius says "push to GitHub":

Create a branch for the task if not already on one: git checkout -b feat/task-name
Stage and commit all changes with a clear conventional commit message (feat:, fix:, chore:, database:, etc.)
Push the branch: git push origin feat/task-name
Remind Marius to open a Pull Request on GitHub so the automated code review runs
Never git push origin main directly. Marius merges branches into main himself via GitHub PRs.

Commit message format:

type: short summary

- bullet describing what changed and why
- another bullet if needed

## CI / Deployment

Two workflows, one per deployed environment, kept structurally identical. Push to `dev` → `.github/workflows/dev.yml`: `mvn -B verify` → image built, scanned, and pushed to GHCR as `:dev` → `RENDER_DEPLOY_HOOK_URL`. Push to `main` → `.github/workflows/prod.yml`: same, tagged `:latest` and `:<sha>` (the SHA tag is what a rollback points at) → `RENDER_PROD_DEPLOY_HOOK_URL`. A failing test still blocks both deploys — `mvn -B verify` is the first real step and every step after it is skipped when it fails.

**The project compiles exactly once per run.** `mvn -B verify` runs the tests (output stays visible in the workflow log) *and* produces the jar. `<finalName>app</finalName>` in the pom fixes that at `target/app.jar`, so nothing downstream has to glob for a version number.

The root `Dockerfile` is still multi-stage and **still self-contained** — `docker build .` on a clean checkout compiles from source with no prior Maven run, which is the local path. A global `ARG JAR_SOURCE` selects where the jar comes from: `build` (the default) compiles it inside the image, `prebuilt` copies `target/app.jar` out of the build context. CI passes `build-args: JAR_SOURCE=prebuilt`, so the Docker build never runs Maven and the project is not compiled a second time. BuildKit only builds the stages the selected target depends on, so the unused branch costs nothing — which also means **BuildKit is required** (the default in Docker 23+, and always what buildx uses).

`.dockerignore` exists for this: `target/*` followed by `!target/app.jar` lets exactly that one file through. Without it every build would upload `classes/`, `test-classes/`, `surefire-reports/` and `app.jar.original` into the context and invalidate the layer cache on every run. It also keeps `.git`, `.github`, `.claude`, `docs`, `database`, `node_modules` and any local `secrets.properties` out of the build context.

Layer caching is `cache-from: type=gha` / `cache-to: type=gha,mode=max` on `docker/build-push-action`, which needs the `docker-container` driver — hence the `docker/setup-buildx-action` step.

**The image is scanned before it is pushed.** The build step uses `load: true` (local daemon only), Trivy scans that image and fails the job on HIGH or CRITICAL, and only then does a second `build-push-action` — all cache hits, upload only — push to GHCR. A vulnerable image therefore never reaches the registry, let alone Render. The scan sets `ignore-unfixed: true`: an unpatched base-image CVE that nobody can act on must not permanently red-light every deploy. The push step also attaches an SBOM and a provenance attestation (`sbom: true`, `provenance: true`), which makes the pushed tag an OCI image index rather than a single manifest.

Both `FROM` lines still use floating tags. Pinning them by digest is a known follow-up — it buys reproducible rebuilds at the cost of a manual bump for every base-image security update.