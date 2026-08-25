package com.platform.dto.user;

import com.platform.entity.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body for {@code PUT /api/users/{id}}, restricted to PLATFORM_ADMIN.
 *
 * <p>Carries no password field: an admin resetting another user's password without their
 * knowledge is an account-takeover primitive, not an administration feature.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminUserUpdateRequest {

    @NotNull(message = "Role is required")
    private User.UserRole role;

    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name must be at most 100 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name must be at most 100 characters")
    private String lastName;

    @Size(max = 30, message = "Phone must be at most 30 characters")
    private String phone;
}
