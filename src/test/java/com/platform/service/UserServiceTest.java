package com.platform.service;

import com.platform.dto.auth.WhoAmIResponseDTO;
import com.platform.dto.user.AdminUserUpdateRequest;
import com.platform.dto.user.UpdateProfileRequest;
import com.platform.dto.user.UserResponseDTO;
import com.platform.entity.Business;
import com.platform.entity.Employee;
import com.platform.entity.Location;
import com.platform.entity.ProvidedService;
import com.platform.entity.User;
import com.platform.exception.BusinessException;
import com.platform.exception.ConflictException;
import com.platform.exception.UserNotFoundException;
import com.platform.repository.BusinessRepository;
import com.platform.repository.EmployeeRepository;
import com.platform.repository.LocationRepository;
import com.platform.repository.ServiceRepository;
import com.platform.repository.UserRepository;
import com.platform.storage.ImageUrlResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock private UserRepository userRepository;
    @Mock private BusinessRepository businessRepository;
    @Mock private ServiceRepository serviceRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private ImageUrlResolver imageUrls;

    private static final String EMAIL = "owner@example.com";

    private User currentUser;

    @BeforeEach
    void setUp() {
        currentUser = user(EMAIL, User.UserRole.BUSINESS_ADMIN);
        authenticateAs(EMAIL);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── whoami: the batched fan-out (BP-53) ───────────────────────────────────

    /**
     * whoami loads the children of every owned business in one query per child type, not
     * one per business. This is the shape the N+1 fix put in place, and it is invisible
     * from the response - a per-business loop would return exactly the same JSON - so this
     * test asserts on the queries rather than on the payload.
     */
    @Test
    void whoami_loadsChildrenInOneBatchQueryPerTypeNotOnePerBusiness() {
        Business first = business(currentUser);
        Business second = business(currentUser);
        List<UUID> bothIds = List.of(first.getId(), second.getId());

        stubCurrentUserLookup();
        when(businessRepository.findByOwnerIdOrderByCreatedAtAsc(currentUser.getId()))
                .thenReturn(List.of(first, second));
        when(serviceRepository.findByBusinessIdIn(bothIds))
                .thenReturn(List.of(service(first), service(second)));
        when(employeeRepository.findByBusinessIdInAndEnabled(bothIds, true))
                .thenReturn(List.of(employee(first)));
        when(locationRepository.findByBusinessIdIn(bothIds))
                .thenReturn(List.of(location(second)));

        WhoAmIResponseDTO response = userService.whoami();

        // One call each, carrying both business ids together.
        verify(serviceRepository).findByBusinessIdIn(bothIds);
        verify(employeeRepository).findByBusinessIdInAndEnabled(bothIds, true);
        verify(locationRepository).findByBusinessIdIn(bothIds);

        // And never the per-business variants that the fan-out used to call.
        verify(serviceRepository, never()).findByBusinessId(any(UUID.class));
        verify(locationRepository, never()).findByBusinessId(any(UUID.class));
    }

    /**
     * The aggregate covers every owned business. This used to read {@code businessList.get(0)}
     * from an unordered query, so a two-business owner saw one business's children, and not
     * reliably the same one each time.
     */
    @Test
    void whoami_aggregatesChildrenAcrossEveryOwnedBusiness() {
        Business first = business(currentUser);
        Business second = business(currentUser);
        List<UUID> bothIds = List.of(first.getId(), second.getId());

        stubCurrentUserLookup();
        when(businessRepository.findByOwnerIdOrderByCreatedAtAsc(currentUser.getId()))
                .thenReturn(List.of(first, second));
        when(serviceRepository.findByBusinessIdIn(bothIds))
                .thenReturn(List.of(service(first), service(second), service(second)));
        when(employeeRepository.findByBusinessIdInAndEnabled(bothIds, true))
                .thenReturn(List.of(employee(first), employee(second)));
        when(locationRepository.findByBusinessIdIn(bothIds))
                .thenReturn(List.of(location(first), location(second)));

        WhoAmIResponseDTO response = userService.whoami();

        assertEquals(2, response.getBusinessList().size());
        assertEquals(3, response.getProvidedServiceList().size());
        assertEquals(2, response.getEmployeeList().size());
        assertEquals(2, response.getLocationList().size());
    }

    /** Owner order is fixed by the query, so two calls cannot disagree about "the first business". */
    @Test
    void whoami_usesTheDeterministicallyOrderedOwnerQuery() {
        Business first = business(currentUser);
        Business second = business(currentUser);

        stubCurrentUserLookup();
        when(businessRepository.findByOwnerIdOrderByCreatedAtAsc(currentUser.getId()))
                .thenReturn(List.of(first, second));
        when(serviceRepository.findByBusinessIdIn(any())).thenReturn(List.of());
        when(employeeRepository.findByBusinessIdInAndEnabled(any(), eq(true))).thenReturn(List.of());
        when(locationRepository.findByBusinessIdIn(any())).thenReturn(List.of());

        WhoAmIResponseDTO response = userService.whoami();

        verify(businessRepository).findByOwnerIdOrderByCreatedAtAsc(currentUser.getId());
        verify(businessRepository, never()).findByOwnerId(any());
        assertEquals(List.of(first.getId(), second.getId()),
                response.getBusinessList().stream().map(b -> b.getId()).toList());
    }

    /** No businesses means no ids to query by, so the three child queries must not run at all. */
    @Test
    void whoami_skipsTheChildQueriesWhenTheUserOwnsNothing() {
        stubCurrentUserLookup();
        when(businessRepository.findByOwnerIdOrderByCreatedAtAsc(currentUser.getId()))
                .thenReturn(List.of());

        WhoAmIResponseDTO response = userService.whoami();

        verifyNoInteractions(serviceRepository);
        verifyNoInteractions(employeeRepository);
        verifyNoInteractions(locationRepository);
        assertTrue(response.getBusinessList().isEmpty());
        assertTrue(response.getProvidedServiceList().isEmpty());
        assertTrue(response.getEmployeeList().isEmpty());
        assertTrue(response.getLocationList().isEmpty());
        assertNotNull(response.getUser());
    }

    /** Disabled employees are soft-deleted staff; whoami must not resurrect them. */
    @Test
    void whoami_asksOnlyForEnabledEmployees() {
        Business only = business(currentUser);

        stubCurrentUserLookup();
        when(businessRepository.findByOwnerIdOrderByCreatedAtAsc(currentUser.getId()))
                .thenReturn(List.of(only));
        when(serviceRepository.findByBusinessIdIn(any())).thenReturn(List.of());
        when(employeeRepository.findByBusinessIdInAndEnabled(any(), eq(true))).thenReturn(List.of());
        when(locationRepository.findByBusinessIdIn(any())).thenReturn(List.of());

        userService.whoami();

        verify(employeeRepository).findByBusinessIdInAndEnabled(List.of(only.getId()), true);
    }

    // ── adminUpdateUser: the self-demotion guard ──────────────────────────────

    /**
     * Role lives in the JWT, and the JWT is not re-checked per request. An admin who
     * demoted themselves would keep PLATFORM_ADMIN until their token expired, so the row
     * and the token would disagree for up to a full token lifetime.
     */
    @Test
    void adminUpdateUser_refusesToLetAnAdminChangeTheirOwnRole() {
        User admin = user("admin@example.com", User.UserRole.PLATFORM_ADMIN);
        authenticateAs(admin.getEmail());
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(userRepository.findByEmailIgnoreCase(admin.getEmail())).thenReturn(Optional.of(admin));

        assertThrows(BusinessException.class,
                () -> userService.adminUpdateUser(admin.getId(),
                        adminRequest(User.UserRole.BUSINESS_ADMIN)));

        verify(userRepository, never()).save(any());
        assertEquals(User.UserRole.PLATFORM_ADMIN, admin.getRole());
    }

    /** The guard is about the role only - an admin may still edit their own name and phone. */
    @Test
    void adminUpdateUser_allowsAnAdminToEditTheirOwnProfileWithTheSameRole() {
        User admin = user("admin@example.com", User.UserRole.PLATFORM_ADMIN);
        authenticateAs(admin.getEmail());
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(userRepository.findByEmailIgnoreCase(admin.getEmail())).thenReturn(Optional.of(admin));
        when(userRepository.save(admin)).thenReturn(admin);

        AdminUserUpdateRequest request = adminRequest(User.UserRole.PLATFORM_ADMIN);
        request.setFirstName("Renamed");

        UserResponseDTO updated = userService.adminUpdateUser(admin.getId(), request);

        assertEquals("Renamed", updated.getFirstName());
        assertEquals(User.UserRole.PLATFORM_ADMIN, updated.getRole());
    }

    @Test
    void adminUpdateUser_mayChangeAnotherUsersRole() {
        User admin = user("admin@example.com", User.UserRole.PLATFORM_ADMIN);
        User target = user("target@example.com", User.UserRole.BUSINESS_ADMIN);
        authenticateAs(admin.getEmail());
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(userRepository.findByEmailIgnoreCase(admin.getEmail())).thenReturn(Optional.of(admin));
        when(userRepository.save(target)).thenReturn(target);

        UserResponseDTO updated =
                userService.adminUpdateUser(target.getId(), adminRequest(User.UserRole.PLATFORM_ADMIN));

        assertEquals(User.UserRole.PLATFORM_ADMIN, updated.getRole());
    }

    @Test
    void adminUpdateUser_rejectsAnUnknownUser() {
        UUID missing = UUID.randomUUID();
        when(userRepository.findById(missing)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
                () -> userService.adminUpdateUser(missing, adminRequest(User.UserRole.BUSINESS_ADMIN)));
    }

    // ── deleteUser: the orphan-business guard ─────────────────────────────────

    /**
     * Deleting an owner would break the FK on {@code businesses.owner_id}. Letting JPA
     * raise that instead gives the caller a generic "conflicts with existing data", which
     * describes the wrong problem - the fix is to move the businesses, and the message has
     * to say so.
     */
    @Test
    void deleteUser_refusesWhileTheUserStillOwnsBusinesses() {
        UUID id = UUID.randomUUID();
        when(userRepository.existsById(id)).thenReturn(true);
        when(businessRepository.existsByOwnerId(id)).thenReturn(true);

        ConflictException error = assertThrows(ConflictException.class, () -> userService.deleteUser(id));
        assertTrue(error.getMessage().contains("owns businesses"), error.getMessage());

        verify(userRepository, never()).deleteById(any());
    }

    @Test
    void deleteUser_deletesOnceTheBusinessesAreGone() {
        UUID id = UUID.randomUUID();
        when(userRepository.existsById(id)).thenReturn(true);
        when(businessRepository.existsByOwnerId(id)).thenReturn(false);

        userService.deleteUser(id);

        verify(userRepository).deleteById(id);
    }

    @Test
    void deleteUser_rejectsAnUnknownUserBeforeCheckingBusinesses() {
        UUID missing = UUID.randomUUID();
        when(userRepository.existsById(missing)).thenReturn(false);

        assertThrows(UserNotFoundException.class, () -> userService.deleteUser(missing));

        verifyNoInteractions(businessRepository);
        verify(userRepository, never()).deleteById(any());
    }

    // ── Identity resolution ───────────────────────────────────────────────────

    @Test
    void getUser_rejectsAnAnonymousCaller() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> userService.getUser());
    }

    @Test
    void getUser_rejectsAnEmptyContext() {
        SecurityContextHolder.clearContext();

        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> userService.getUser());
    }

    @Test
    void getUserByUsername_normalizesBeforeLookingUp() {
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(currentUser));

        assertEquals(currentUser, userService.getUserByUsername("  Owner@Example.COM  "));

        verify(userRepository).findByEmailIgnoreCase(EMAIL);
    }

    @Test
    void getUserByUsername_rejectsAnUnknownAddress() {
        when(userRepository.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> userService.getUserByUsername("nobody@example.com"));
    }

    /**
     * {@code Locale.ROOT} is load-bearing here. Under a Turkish default locale
     * {@code "I".toLowerCase()} is "ı" (dotless), so a locale-sensitive lowercase would
     * register accounts under an address nobody could ever log in with.
     */
    @Test
    void normalizeEmail_isNotAffectedByTheDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));

            assertEquals("admin@example.com", UserService.normalizeEmail("ADMIN@EXAMPLE.COM"));
            assertEquals("istanbul@example.com", UserService.normalizeEmail("ISTANBUL@EXAMPLE.COM"));
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void normalizeEmail_trimsAndPassesNullThrough() {
        assertEquals("a@b.io", UserService.normalizeEmail("  A@B.io  "));
        assertEquals(null, UserService.normalizeEmail(null));
    }

    // ── Batch lookup (BP-53) ──────────────────────────────────────────────────

    @Test
    void getUsersByIds_keysTheResultById() {
        User first = user("a@example.com", User.UserRole.BUSINESS_ADMIN);
        User second = user("b@example.com", User.UserRole.BUSINESS_ADMIN);
        Set<UUID> ids = Set.of(first.getId(), second.getId());
        when(userRepository.findAllById(ids)).thenReturn(List.of(first, second));

        Map<UUID, User> byId = userService.getUsersByIds(ids);

        assertEquals(first, byId.get(first.getId()));
        assertEquals(second, byId.get(second.getId()));
    }

    /** An empty id set must not reach the repository - {@code IN ()} is not valid SQL. */
    @Test
    void getUsersByIds_shortCircuitsOnAnEmptyCollection() {
        assertEquals(Map.of(), userService.getUsersByIds(List.of()));

        verifyNoInteractions(userRepository);
    }

    // ── Self-service profile ──────────────────────────────────────────────────

    @Test
    void updateMyProfile_writesNameAndPhoneAndLeavesRoleAndEmailAlone() {
        stubCurrentUserLookup();
        when(userRepository.save(currentUser)).thenReturn(currentUser);

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFirstName("New");
        request.setLastName("Name");
        request.setPhone("+37360000000");

        UserResponseDTO updated = userService.updateMyProfile(request);

        assertEquals("New", updated.getFirstName());
        assertEquals("Name", updated.getLastName());
        assertEquals("+37360000000", updated.getPhone());
        // UpdateProfileRequest carries no role or email field, so neither can move.
        assertEquals(EMAIL, updated.getEmail());
        assertEquals(User.UserRole.BUSINESS_ADMIN, updated.getRole());
    }

    @Test
    void getMyProfile_returnsTheAuthenticatedUser() {
        stubCurrentUserLookup();

        assertEquals(EMAIL, userService.getMyProfile().getEmail());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void stubCurrentUserLookup() {
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(currentUser));
    }

    private void authenticateAs(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null,
                        List.of(new SimpleGrantedAuthority("ROLE_BUSINESS_ADMIN"))));
    }

    private User user(String email, User.UserRole role) {
        return User.builder().id(UUID.randomUUID()).email(email).role(role).build();
    }

    private Business business(User owner) {
        return Business.builder().id(UUID.randomUUID()).owner(owner).name("Salon").build();
    }

    private ProvidedService service(Business business) {
        return ProvidedService.builder().id(UUID.randomUUID()).business(business).name("Cut").build();
    }

    private Employee employee(Business business) {
        return Employee.builder().id(UUID.randomUUID()).business(business).firstName("Ana").build();
    }

    private Location location(Business business) {
        return Location.builder().id(UUID.randomUUID()).business(business).build();
    }

    private AdminUserUpdateRequest adminRequest(User.UserRole role) {
        AdminUserUpdateRequest request = new AdminUserUpdateRequest();
        request.setRole(role);
        request.setFirstName("First");
        request.setLastName("Last");
        request.setPhone("+37360000001");
        return request;
    }
}
