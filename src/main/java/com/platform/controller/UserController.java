package com.platform.controller;

import com.platform.dto.auth.WhoAmIResponseDTO;
import com.platform.dto.user.AdminUserUpdateRequest;
import com.platform.dto.user.UpdateProfileRequest;
import com.platform.dto.user.UserResponseDTO;
import com.platform.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import com.platform.utils.PageRequests;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Self-service endpoints live under {@code /me} and derive identity from the security context.
 * Everything addressed by {@code {id}} is PLATFORM_ADMIN-only.
 *
 * <p>There is deliberately no {@code @PreAuthorize("#id == principal")} self-service variant:
 * {@code principal} here is an email String while {@code #id} is a UUID, so such an expression
 * silently evaluates false. Taking the id from the context instead means the client never
 * supplies an identifier it could tamper with.
 */
@Tag(name = "User", description = "User management endpoints")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    // ── Self-service ──────────────────────────────────────────────────────────

    @Operation(summary = "Who am I",
            description = "Returns the currently authenticated user together with their businesses, services and employees")
    @ApiResponse(responseCode = "200", description = "Authenticated user retrieved successfully")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @GetMapping("/whoami")
    public ResponseEntity<WhoAmIResponseDTO> whoami() {
        return ResponseEntity.ok(userService.whoami());
    }

    @Operation(summary = "Get my profile",
            description = "Returns the currently authenticated user's own profile")
    @ApiResponse(responseCode = "200", description = "Profile retrieved successfully")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @GetMapping("/me")
    public ResponseEntity<UserResponseDTO> getMyProfile() {
        return ResponseEntity.ok(userService.getMyProfile());
    }

    @Operation(summary = "Update my profile",
            description = "Updates the authenticated user's own name and phone. Cannot change role, email or password.")
    @ApiResponse(responseCode = "200", description = "Profile updated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @PutMapping("/me")
    public ResponseEntity<UserResponseDTO> updateMyProfile(
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateMyProfile(request));
    }

    // ── Administration ────────────────────────────────────────────────────────

    @Operation(summary = "List users", description = "Returns a paginated list of all users")
    @ApiResponse(responseCode = "200", description = "Users retrieved successfully")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "403", description = "Not authorized to list users")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @GetMapping
    public ResponseEntity<Page<UserResponseDTO>> listUsers(
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "10")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort as 'field' or 'field,asc|desc'", example = "email,asc")
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(userService.listUsers(
                PageRequests.of(page, size, sort, UserService.SORTABLE_FIELDS, UserService.DEFAULT_SORT)));
    }

    @Operation(summary = "Get user by ID", description = "Returns a single user by their UUID")
    @ApiResponse(responseCode = "200", description = "User found")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "403", description = "Not authorized to view this user")
    @ApiResponse(responseCode = "404", description = "User not found")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDTO> getUserById(
            @Parameter(description = "User UUID", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID id) {
        return ResponseEntity.ok(userService.getUserDTOById(id));
    }

    @Operation(summary = "Update a user",
            description = "Updates a user's role and profile. Administrators cannot change their own role.")
    @ApiResponse(responseCode = "200", description = "User updated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "403", description = "Not authorized to update this user")
    @ApiResponse(responseCode = "404", description = "User not found")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<UserResponseDTO> adminUpdateUser(
            @Parameter(description = "User UUID", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID id,
            @Valid @RequestBody AdminUserUpdateRequest request) {
        return ResponseEntity.ok(userService.adminUpdateUser(id, request));
    }

    @Operation(summary = "Delete user",
            description = "Deletes a user. Fails if the user still owns businesses.")
    @ApiResponse(responseCode = "204", description = "User deleted successfully")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "403", description = "Not authorized to delete this user")
    @ApiResponse(responseCode = "404", description = "User not found")
    @ApiResponse(responseCode = "409", description = "User still owns businesses")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(
            @Parameter(description = "User UUID", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
