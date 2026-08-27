package com.platform.dto.employee;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeRequestDTO {

    @NotBlank(message = "Employee first name is required")
    @Size(min = 2, max = 100, message = "Employee first name must be between 2 and 100 characters")
    private String firstName;

    @NotBlank(message = "Employee last name is required")
    @Size(min = 2, max = 100, message = "Employee last name must be between 2 and 100 characters")
    private String lastName;

    @Email(message = "Employee email must be a valid email address")
    @Size(max = 255, message = "Employee email must not exceed 255 characters")
    private String email;

    private String phoneNumber;

    // Photo changes only through /api/business/{id}/employee/{id}/images.
    private Boolean enabled;
}
