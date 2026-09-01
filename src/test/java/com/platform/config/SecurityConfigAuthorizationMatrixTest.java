package com.platform.config;

import com.platform.security.JwtAuthenticationFilter;
import com.platform.security.RestAccessDeniedHandler;
import com.platform.security.RestAuthenticationEntryPoint;
import com.platform.service.AnalyticsService;
import com.platform.service.AuthService;
import com.platform.service.AvailabilityService;
import com.platform.service.BookingService;
import com.platform.service.BusinessService;
import com.platform.service.BusinessWorkingHoursService;
import com.platform.service.EmployeeLocationServicePriceService;
import com.platform.service.EmployeeService;
import com.platform.service.FeatureService;
import com.platform.service.ImageService;
import com.platform.service.LocationService;
import com.platform.service.PasswordService;
import com.platform.service.ProvidedServicesService;
import com.platform.service.ReviewService;
import com.platform.service.UserService;
import com.platform.utils.JwtUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.stream.Stream;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Table-driven authorization matrix for {@link SecurityConfig} (BP-62).
 *
 * <p>{@code SecurityConfig} is a ~100-line first-match-wins ladder whose ordering is
 * load-bearing - its own comments say so. Before this test not one rule was covered,
 * and every routing bug in the security epic was found by reading, not by a red test.
 * Adding a rule in the wrong position silently opens or closes endpoints; this pins
 * the effective decision for a representative endpoint in every section so the next
 * reorder cannot move one without turning a row red.
 *
 * <h2>What each outcome asserts</h2>
 * <ul>
 *   <li>{@code PERMIT} - the filter chain let the request through to dispatch. The
 *       downstream services are {@link MockBean}s, so the controller response itself
 *       is arbitrary (200 with a null body, 400 on bean validation, 404 for a path no
 *       controller serves); the assertion is only that the status is <em>not</em> 401
 *       and <em>not</em> 403.</li>
 *   <li>{@code UNAUTHENTICATED} - 401, the anonymous caller was stopped by
 *       {@link RestAuthenticationEntryPoint}.</li>
 *   <li>{@code FORBIDDEN} - 403, an authenticated caller in the wrong role was stopped
 *       by {@link RestAccessDeniedHandler}.</li>
 * </ul>
 *
 * <h2>Rows tagged KNOWN-GAP</h2>
 * A handful of rows pin behaviour the review flagged as wrong (PLATFORM_ADMIN locked
 * out of resource creation, {@code /api/business/admin/**} guarding a path no
 * controller serves, {@code PUBLIC_GET_PATTERNS} applying to every HTTP method). They
 * assert what the config <em>does</em> today, not what it should do, so the suite is
 * green on {@code dev}; when the corresponding fix lands, the failing row is the
 * reminder to update the expectation.
 */
@WebMvcTest
@Import(SecurityConfigAuthorizationMatrixTest.SecurityChainUnderTest.class)
@DisplayName("SecurityConfig authorization matrix")
class SecurityConfigAuthorizationMatrixTest {

    /**
     * Pulls the real {@link SecurityConfig} and its collaborators into the
     * {@code @WebMvcTest} slice, which otherwise component-scans only controllers.
     * The two custom filters are imported as real classes, never {@code @MockBean}s -
     * a mocked {@code Filter} never calls {@code chain.doFilter(...)}, so every request
     * would die in the filter and come back an empty 200, testing nothing.
     */
    @org.springframework.boot.test.context.TestConfiguration
    @Import({
            SecurityConfig.class,
            RateLimitConfig.class,
            JwtAuthenticationFilter.class,
            RestAuthenticationEntryPoint.class,
            RestAccessDeniedHandler.class
    })
    static class SecurityChainUnderTest {
    }

    @Autowired
    private MockMvc mvc;

    // JwtUtils is only reached from a real Authorization header; every test here
    // authenticates through SecurityMockMvcRequestPostProcessors instead, so the mock
    // is never called - it exists so JwtAuthenticationFilter can be constructed
    // without a jwt.secret property.
    @MockBean private JwtUtils jwtUtils;

    // Every service a controller constructor requires. None is invoked on a 401/403
    // path; on a PERMIT path the controller runs against these no-op mocks.
    @MockBean private AnalyticsService analyticsService;
    @MockBean private AppConfig appConfig;
    @MockBean private AuthService authService;
    @MockBean private AvailabilityService availabilityService;
    @MockBean private BookingService bookingService;
    @MockBean private BusinessService businessService;
    @MockBean private BusinessWorkingHoursService businessWorkingHoursService;
    @MockBean private EmployeeLocationServicePriceService employeeLocationServicePriceService;
    @MockBean private EmployeeService employeeService;
    @MockBean private FeatureService featureService;
    @MockBean private ImageService imageService;
    @MockBean private LocationService locationService;
    @MockBean private PasswordService passwordService;
    @MockBean private ProvidedServicesService providedServicesService;
    @MockBean private ReviewService reviewService;
    @MockBean private UserService userService;

    private enum Principal { ANON, BUSINESS_ADMIN, PLATFORM_ADMIN }

    private enum Outcome { PERMIT, UNAUTHENTICATED, FORBIDDEN }

    @ParameterizedTest(name = "{2} {0} {1} -> {3}")
    @MethodSource("matrix")
    void enforcesTheExpectedDecision(HttpMethod method, String path, Principal principal, Outcome expected)
            throws Exception {

        MockHttpServletRequestBuilder req = request(method, path).with(switch (principal) {
            case ANON -> anonymous();
            case BUSINESS_ADMIN -> user("owner@example.com").roles("BUSINESS_ADMIN");
            case PLATFORM_ADMIN -> user("admin@example.com").roles("PLATFORM_ADMIN");
        });
        if (method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH) {
            req = req.contentType("application/json").content("{}");
        }

        ResultActions result = mvc.perform(req);

        switch (expected) {
            case UNAUTHENTICATED -> result.andExpect(status().isUnauthorized());
            case FORBIDDEN -> result.andExpect(status().isForbidden());
            case PERMIT -> {
                int s = result.andReturn().getResponse().getStatus();
                org.junit.jupiter.api.Assertions.assertTrue(
                        s != 401 && s != 403,
                        "expected the filter chain to permit this request, but got " + s);
            }
        }
    }

    private static Arguments row(HttpMethod m, String path, Principal p, Outcome o) {
        return Arguments.of(m, path, p, o);
    }

    static Stream<Arguments> matrix() {
        String biz = "/api/business/11111111-1111-1111-1111-111111111111";
        return Stream.of(

                // ── Public POSTs (PUBLIC_POST_PATTERNS) ───────────────────────────
                row(HttpMethod.POST, "/api/auth/login", Principal.ANON, Outcome.PERMIT),
                row(HttpMethod.POST, "/api/auth/register", Principal.ANON, Outcome.PERMIT),
                row(HttpMethod.POST, "/api/auth/refresh", Principal.ANON, Outcome.PERMIT),
                row(HttpMethod.POST, "/api/auth/logout", Principal.ANON, Outcome.PERMIT),
                row(HttpMethod.POST, "/api/auth/forgot-password", Principal.ANON, Outcome.PERMIT),
                row(HttpMethod.POST, "/api/auth/reset-password", Principal.ANON, Outcome.PERMIT),
                row(HttpMethod.POST, "/api/booking", Principal.ANON, Outcome.PERMIT),
                // change-password is deliberately NOT public - needs the session
                row(HttpMethod.POST, "/api/auth/change-password", Principal.ANON, Outcome.UNAUTHENTICATED),
                row(HttpMethod.POST, "/api/auth/change-password", Principal.BUSINESS_ADMIN, Outcome.PERMIT),

                // ── Public GETs (PUBLIC_GET_PATTERNS) ─────────────────────────────
                row(HttpMethod.GET, "/api/health", Principal.ANON, Outcome.PERMIT),
                row(HttpMethod.GET, "/config", Principal.ANON, Outcome.PERMIT),
                row(HttpMethod.GET, "/v3/api-docs", Principal.ANON, Outcome.PERMIT),
                // KNOWN-GAP: PUBLIC_GET_PATTERNS is registered with no HttpMethod, so it
                // is permitAll for every verb, not just GET. A POST to /api/health has no
                // handler (404) so it still reads as PERMIT here - the point is the rule
                // does not scope itself to GET.
                row(HttpMethod.POST, "/api/health", Principal.ANON, Outcome.PERMIT),

                // ── Business CRUD (section 7 catch-all) ───────────────────────────
                row(HttpMethod.GET, "/api/business", Principal.ANON, Outcome.PERMIT),
                row(HttpMethod.GET, biz, Principal.ANON, Outcome.PERMIT),
                row(HttpMethod.GET, "/api/business/slug/some-slug", Principal.ANON, Outcome.PERMIT),
                row(HttpMethod.POST, "/api/business", Principal.ANON, Outcome.UNAUTHENTICATED),
                row(HttpMethod.POST, "/api/business", Principal.BUSINESS_ADMIN, Outcome.PERMIT),
                // KNOWN-GAP: services allow a PLATFORM_ADMIN to act on any business, but
                // the URL rule for POST/PUT/DELETE /api/business/** is BUSINESS_ADMIN-only.
                row(HttpMethod.POST, "/api/business", Principal.PLATFORM_ADMIN, Outcome.FORBIDDEN),
                row(HttpMethod.PUT, biz, Principal.BUSINESS_ADMIN, Outcome.PERMIT),
                row(HttpMethod.PUT, biz, Principal.PLATFORM_ADMIN, Outcome.FORBIDDEN),
                row(HttpMethod.DELETE, biz, Principal.BUSINESS_ADMIN, Outcome.PERMIT),

                // ── /api/business/admin/** (section 2) ────────────────────────────
                // KNOWN-GAP: no controller serves this path. The rule still gates it:
                // anon 401, BUSINESS_ADMIN 403, PLATFORM_ADMIN through to a 404 (PERMIT).
                row(HttpMethod.GET, "/api/business/admin/anything", Principal.ANON, Outcome.UNAUTHENTICATED),
                row(HttpMethod.GET, "/api/business/admin/anything", Principal.BUSINESS_ADMIN, Outcome.FORBIDDEN),
                row(HttpMethod.GET, "/api/business/admin/anything", Principal.PLATFORM_ADMIN, Outcome.PERMIT),

                // ── Employee endpoints (section 3) ───────────────────────────────
                row(HttpMethod.GET, biz + "/employee", Principal.ANON, Outcome.PERMIT),
                row(HttpMethod.GET, biz + "/employee/22222222-2222-2222-2222-222222222222", Principal.ANON, Outcome.PERMIT),
                row(HttpMethod.GET, biz + "/employee/22222222-2222-2222-2222-222222222222/availability", Principal.ANON, Outcome.PERMIT),
                row(HttpMethod.POST, biz + "/employee", Principal.ANON, Outcome.UNAUTHENTICATED),
                row(HttpMethod.POST, biz + "/employee", Principal.BUSINESS_ADMIN, Outcome.PERMIT),
                // KNOWN-GAP: POST employee is BUSINESS_ADMIN-only, PLATFORM_ADMIN cannot create.
                row(HttpMethod.POST, biz + "/employee", Principal.PLATFORM_ADMIN, Outcome.FORBIDDEN),
                row(HttpMethod.PUT, biz + "/employee/22222222-2222-2222-2222-222222222222", Principal.BUSINESS_ADMIN, Outcome.PERMIT),
                row(HttpMethod.PUT, biz + "/employee/22222222-2222-2222-2222-222222222222", Principal.PLATFORM_ADMIN, Outcome.PERMIT),
                row(HttpMethod.DELETE, biz + "/employee/22222222-2222-2222-2222-222222222222", Principal.BUSINESS_ADMIN, Outcome.PERMIT),
                row(HttpMethod.DELETE, biz + "/employee/22222222-2222-2222-2222-222222222222", Principal.PLATFORM_ADMIN, Outcome.FORBIDDEN),
                row(HttpMethod.DELETE, biz + "/employee/22222222-2222-2222-2222-222222222222/permanent", Principal.BUSINESS_ADMIN, Outcome.FORBIDDEN),
                row(HttpMethod.DELETE, biz + "/employee/22222222-2222-2222-2222-222222222222/permanent", Principal.PLATFORM_ADMIN, Outcome.PERMIT),
                row(HttpMethod.GET, biz + "/employee/22222222-2222-2222-2222-222222222222/admin", Principal.BUSINESS_ADMIN, Outcome.FORBIDDEN),
                row(HttpMethod.GET, biz + "/employee/22222222-2222-2222-2222-222222222222/admin", Principal.PLATFORM_ADMIN, Outcome.PERMIT),

                // ── Image endpoints (section 2b) ─────────────────────────────────
                row(HttpMethod.POST, biz + "/images/upload-url", Principal.ANON, Outcome.UNAUTHENTICATED),
                row(HttpMethod.POST, biz + "/images/upload-url", Principal.BUSINESS_ADMIN, Outcome.PERMIT),
                row(HttpMethod.POST, biz + "/images/upload-url", Principal.PLATFORM_ADMIN, Outcome.PERMIT),
                row(HttpMethod.PUT, biz + "/images", Principal.PLATFORM_ADMIN, Outcome.PERMIT),

                // ── Working-hours endpoints (section 4) ──────────────────────────
                row(HttpMethod.GET, biz + "/working-hours", Principal.ANON, Outcome.UNAUTHENTICATED),
                row(HttpMethod.GET, biz + "/working-hours", Principal.BUSINESS_ADMIN, Outcome.PERMIT),
                row(HttpMethod.POST, biz + "/working-hours", Principal.BUSINESS_ADMIN, Outcome.PERMIT),
                row(HttpMethod.POST, biz + "/working-hours", Principal.PLATFORM_ADMIN, Outcome.FORBIDDEN),
                row(HttpMethod.DELETE, biz + "/working-hours/1", Principal.BUSINESS_ADMIN, Outcome.PERMIT),

                // ── Features endpoints (section 5) ───────────────────────────────
                row(HttpMethod.GET, biz + "/features", Principal.ANON, Outcome.UNAUTHENTICATED),
                row(HttpMethod.GET, biz + "/features", Principal.BUSINESS_ADMIN, Outcome.PERMIT),
                row(HttpMethod.POST, biz + "/features", Principal.BUSINESS_ADMIN, Outcome.PERMIT),
                row(HttpMethod.POST, biz + "/features", Principal.PLATFORM_ADMIN, Outcome.FORBIDDEN),
                row(HttpMethod.DELETE, biz + "/features/1", Principal.BUSINESS_ADMIN, Outcome.PERMIT),

                // ── Service endpoints (sections 6 + 7) ───────────────────────────
                row(HttpMethod.GET, biz + "/service", Principal.ANON, Outcome.PERMIT),
                row(HttpMethod.GET, biz + "/service/active", Principal.ANON, Outcome.PERMIT),
                row(HttpMethod.GET, biz + "/service/33333333-3333-3333-3333-333333333333", Principal.ANON, Outcome.PERMIT),
                row(HttpMethod.POST, biz + "/service", Principal.ANON, Outcome.UNAUTHENTICATED),
                row(HttpMethod.POST, biz + "/service", Principal.BUSINESS_ADMIN, Outcome.PERMIT),
                row(HttpMethod.POST, biz + "/service", Principal.PLATFORM_ADMIN, Outcome.FORBIDDEN),
                row(HttpMethod.PUT, biz + "/service/33333333-3333-3333-3333-333333333333", Principal.BUSINESS_ADMIN, Outcome.PERMIT),

                // ── Location endpoints (section 6b) ──────────────────────────────
                row(HttpMethod.GET, biz + "/location", Principal.ANON, Outcome.PERMIT),
                row(HttpMethod.GET, biz + "/location/44444444-4444-4444-4444-444444444444", Principal.ANON, Outcome.PERMIT),
                row(HttpMethod.POST, biz + "/location", Principal.BUSINESS_ADMIN, Outcome.PERMIT),
                row(HttpMethod.POST, biz + "/location", Principal.PLATFORM_ADMIN, Outcome.FORBIDDEN),
                row(HttpMethod.DELETE, biz + "/location/44444444-4444-4444-4444-444444444444", Principal.BUSINESS_ADMIN, Outcome.PERMIT),

                // ── Pricing endpoints (section 6c) ──────────────────────────────
                row(HttpMethod.GET, biz + "/employee-service-price/55555555-5555-5555-5555-555555555555", Principal.ANON, Outcome.UNAUTHENTICATED),
                row(HttpMethod.GET, biz + "/employee-service-price/55555555-5555-5555-5555-555555555555", Principal.BUSINESS_ADMIN, Outcome.PERMIT),
                row(HttpMethod.GET, biz + "/employee-service-price/55555555-5555-5555-5555-555555555555", Principal.PLATFORM_ADMIN, Outcome.PERMIT),
                row(HttpMethod.POST, biz + "/employee-service-price", Principal.BUSINESS_ADMIN, Outcome.PERMIT),
                row(HttpMethod.POST, biz + "/employee-service-price", Principal.PLATFORM_ADMIN, Outcome.FORBIDDEN),

                // ── Analytics endpoints (section 7b) ─────────────────────────────
                row(HttpMethod.GET, "/api/analytics/dashboard/11111111-1111-1111-1111-111111111111", Principal.ANON, Outcome.UNAUTHENTICATED),
                row(HttpMethod.GET, "/api/analytics/dashboard/11111111-1111-1111-1111-111111111111", Principal.BUSINESS_ADMIN, Outcome.PERMIT),
                row(HttpMethod.GET, "/api/analytics/dashboard/11111111-1111-1111-1111-111111111111", Principal.PLATFORM_ADMIN, Outcome.PERMIT),
                // Non-GET under /api/analytics is denyAll, even for PLATFORM_ADMIN.
                row(HttpMethod.POST, "/api/analytics/anything", Principal.PLATFORM_ADMIN, Outcome.FORBIDDEN),
                row(HttpMethod.POST, "/api/analytics/anything", Principal.ANON, Outcome.UNAUTHENTICATED),

                // ── User endpoints (section 8) ──────────────────────────────────
                row(HttpMethod.GET, "/api/users/whoami", Principal.ANON, Outcome.UNAUTHENTICATED),
                row(HttpMethod.GET, "/api/users/whoami", Principal.BUSINESS_ADMIN, Outcome.PERMIT),
                row(HttpMethod.GET, "/api/users/me", Principal.BUSINESS_ADMIN, Outcome.PERMIT),
                row(HttpMethod.PUT, "/api/users/me", Principal.BUSINESS_ADMIN, Outcome.PERMIT),
                row(HttpMethod.GET, "/api/users", Principal.BUSINESS_ADMIN, Outcome.FORBIDDEN),
                row(HttpMethod.GET, "/api/users", Principal.PLATFORM_ADMIN, Outcome.PERMIT),
                row(HttpMethod.GET, "/api/users/66666666-6666-6666-6666-666666666666", Principal.BUSINESS_ADMIN, Outcome.FORBIDDEN),
                row(HttpMethod.DELETE, "/api/users/66666666-6666-6666-6666-666666666666", Principal.BUSINESS_ADMIN, Outcome.FORBIDDEN),
                row(HttpMethod.DELETE, "/api/users/66666666-6666-6666-6666-666666666666", Principal.PLATFORM_ADMIN, Outcome.PERMIT),

                // ── Booking & Review endpoints (section 9) ──────────────────────
                row(HttpMethod.GET, "/api/booking/77777777-7777-7777-7777-777777777777", Principal.ANON, Outcome.UNAUTHENTICATED),
                row(HttpMethod.GET, "/api/booking/77777777-7777-7777-7777-777777777777", Principal.BUSINESS_ADMIN, Outcome.PERMIT),
                row(HttpMethod.GET, "/api/booking/77777777-7777-7777-7777-777777777777", Principal.PLATFORM_ADMIN, Outcome.PERMIT),
                row(HttpMethod.GET, "/api/booking", Principal.BUSINESS_ADMIN, Outcome.PERMIT),
                row(HttpMethod.PATCH, "/api/booking/77777777-7777-7777-7777-777777777777/status", Principal.ANON, Outcome.UNAUTHENTICATED),
                row(HttpMethod.PATCH, "/api/booking/77777777-7777-7777-7777-777777777777/status", Principal.BUSINESS_ADMIN, Outcome.PERMIT),
                row(HttpMethod.PATCH, "/api/booking/77777777-7777-7777-7777-777777777777/status", Principal.PLATFORM_ADMIN, Outcome.PERMIT),
                row(HttpMethod.DELETE, "/api/booking/77777777-7777-7777-7777-777777777777", Principal.BUSINESS_ADMIN, Outcome.PERMIT),
                row(HttpMethod.POST, "/api/review/booking/77777777-7777-7777-7777-777777777777", Principal.ANON, Outcome.UNAUTHENTICATED),
                row(HttpMethod.POST, "/api/review/booking/77777777-7777-7777-7777-777777777777", Principal.BUSINESS_ADMIN, Outcome.PERMIT),
                row(HttpMethod.GET, "/api/review/business/11111111-1111-1111-1111-111111111111", Principal.BUSINESS_ADMIN, Outcome.PERMIT),

                // ── Default deny (section 10) ───────────────────────────────────
                row(HttpMethod.GET, "/api/something-with-no-rule", Principal.ANON, Outcome.UNAUTHENTICATED),
                row(HttpMethod.GET, "/api/something-with-no-rule", Principal.BUSINESS_ADMIN, Outcome.PERMIT)
        );
    }
}
