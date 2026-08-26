package com.platform.dto.employee;

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

    private String email;

    private String phoneNumber;

    private String photoUrl;

    private Boolean enabled;
}
