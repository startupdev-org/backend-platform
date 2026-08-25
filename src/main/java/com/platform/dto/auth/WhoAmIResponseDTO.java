package com.platform.dto.auth;

import com.platform.dto.business.BusinessResponseDTO;
import com.platform.dto.employee.EmployeeResponseDTO;
import com.platform.dto.service.ServiceResponseDTO;
import com.platform.dto.user.UserResponseDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response for {@code GET /api/users/whoami}.
 *
 * <p>These are DTOs, not entities. Holding a {@code User} here serialized its BCrypt password
 * hash to every caller; holding the others let Jackson walk lazy associations and
 * back-references outside any transaction.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhoAmIResponseDTO {
    private UserResponseDTO user;
    private List<BusinessResponseDTO> businessList;
    private List<ServiceResponseDTO> providedServiceList;
    private List<EmployeeResponseDTO> employeeList;
}
