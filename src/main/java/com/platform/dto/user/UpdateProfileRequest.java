package com.platform.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body for {@code PUT /api/users/me}.
 *
 * <p>Deliberately has no {@code role}, {@code isEnabled}, {@code password} or {@code email}
 * field. Privilege escalation is closed by the shape of this type rather than by a runtime
 * check, so it cannot be reintroduced by a careless edit to a conditional.
 *
 * <p>Email is absent because changing the login identifier needs a confirmation mail to the
 * new address; without one, a stolen token locks the real owner out permanently.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProfileRequest {

    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name must be at most 100 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name must be at most 100 characters")
    private String lastName;

    @Size(max = 30, message = "Phone must be at most 30 characters")
    private String phone;
}
