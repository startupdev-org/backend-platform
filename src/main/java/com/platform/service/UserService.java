package com.platform.service;

import com.platform.dto.auth.WhoAmIResponseDTO;
import com.platform.dto.business.BusinessMapper;
import com.platform.dto.business.BusinessResponseDTO;
import com.platform.dto.employee.EmployeeMapper;
import com.platform.dto.location.LocationMapper;
import com.platform.dto.service.ServiceMapper;
import com.platform.dto.user.AdminUserUpdateRequest;
import com.platform.dto.user.UpdateProfileRequest;
import com.platform.dto.user.UserResponseDTO;
import com.platform.entity.*;
import com.platform.exception.BusinessException;
import com.platform.exception.ConflictException;
import com.platform.exception.UserNotFoundException;
import com.platform.repository.*;
import com.platform.storage.ImageUrlResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final ServiceRepository serviceRepository;
    private final EmployeeRepository employeeRepository;
    private final LocationRepository locationRepository;
    private final ImageUrlResolver imageUrls;

    // ── Current user ──────────────────────────────────────────────────────────

    /**
     * The authenticated caller.
     *
     * <p>Reads the principal via {@code auth.getName()} rather than casting
     * {@code getPrincipal()} to String, so it keeps working if the principal ever becomes a
     * {@code UserDetails} - which it will if the per-request DB recheck is added.
     */
    public User getUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("No authenticated user");
        }
        return getUserByUsername(auth.getName());
    }

    public User getUserByUsername(String username) {
        return userRepository.findByEmailIgnoreCase(normalizeEmail(username))
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    /**
     * Emails are a case-insensitive identity.
     *
     * <p>{@link Locale#ROOT} is load-bearing: under a Turkish default locale
     * {@code "I".toLowerCase()} yields {@code "ı"}, which would silently create accounts
     * nobody can log into.
     */
    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    // ── Self-service ──────────────────────────────────────────────────────────

    public UserResponseDTO getMyProfile() {
        return toResponseDTO(getUser());
    }

    /**
     * Updates the caller's own profile. Cannot reach {@code role}, {@code isEnabled},
     * {@code password} or {@code email} - see {@link UpdateProfileRequest}.
     */
    @Transactional
    public UserResponseDTO updateMyProfile(UpdateProfileRequest request) {
        User user = getUser();
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setPhone(request.getPhone());
        return toResponseDTO(userRepository.save(user));
    }

    public WhoAmIResponseDTO whoami() {
        User user = getUser();

        List<Business> businessList = businessRepository.findByOwnerIdOrderByCreatedAtAsc(user.getId());
        List<UUID> businessIds = businessList.stream().map(Business::getId).toList();

        // Aggregated across every owned business. This used to read businessList.get(0) from
        // an unordered query, which was both wrong and nondeterministic for multi-business
        // owners; the batch lookups also remove the two-queries-per-business N+1.
        List<ProvidedService> services = businessIds.isEmpty()
                ? List.of() : serviceRepository.findByBusinessIdIn(businessIds);
        List<Employee> employees = businessIds.isEmpty()
                ? List.of() : employeeRepository.findByBusinessIdInAndEnabled(businessIds, true);
        List<Location> locations = businessIds.isEmpty()
                ? List.of() : locationRepository.findByBusinessIdIn(businessIds);

        // DTOs, never entities: entities would carry the password hash and let Jackson walk
        // lazy associations outside the transaction.
        List<BusinessResponseDTO> businessDTOs = businessList.stream()
                .map(b -> BusinessMapper.toDTO(b, List.of(), List.of(), Set.of(), user, imageUrls))
                .toList();

        return WhoAmIResponseDTO.builder()
                .user(toResponseDTO(user))
                .businessList(businessDTOs)
                .providedServiceList(ServiceMapper.toDTOList(services))
                .employeeList(EmployeeMapper.toDTOList(employees, imageUrls))
                .locationList(LocationMapper.toDTOList(locations))
                .build();
    }

    // ── Administration ────────────────────────────────────────────────────────

    public Page<UserResponseDTO> listUsers(Pageable pageable) {
        return userRepository.findAll(pageable)
                .map(this::toResponseDTO);
    }

    public User getUserById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
    }

    public UserResponseDTO getUserDTOById(UUID id) {
        return toResponseDTO(getUserById(id));
    }

    @Transactional
    public UserResponseDTO adminUpdateUser(UUID id, AdminUserUpdateRequest request) {
        User target = getUserById(id);

        // An admin demoting themselves would keep the old role in their JWT until it expires,
        // leaving a confusing window where the token outranks the row.
        if (target.getId().equals(getUser().getId()) && request.getRole() != target.getRole()) {
            throw new BusinessException("An administrator cannot change their own role");
        }

        target.setRole(request.getRole());
        target.setFirstName(request.getFirstName());
        target.setLastName(request.getLastName());
        target.setPhone(request.getPhone());

        return toResponseDTO(userRepository.save(target));
    }

    @Transactional
    public void deleteUser(UUID id) {
        if (!userRepository.existsById(id)) {
            throw new UserNotFoundException("User not found with id: " + id);
        }
        // Without this the FK on businesses.owner_id raises a DataIntegrityViolationException,
        // which the handler reports as a generic conflict - describing the wrong problem.
        if (businessRepository.existsByOwnerId(id)) {
            throw new ConflictException(
                    "Cannot delete a user who still owns businesses; transfer or delete them first");
        }
        userRepository.deleteById(id);
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private UserResponseDTO toResponseDTO(User user) {
        return UserResponseDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(user.getPhone())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
