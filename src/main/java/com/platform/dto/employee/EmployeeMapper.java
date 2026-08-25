package com.platform.dto.employee;

import com.platform.entity.Employee;

import java.util.List;

/**
 * Employee -> DTO mapping, mirroring the existing {@code BusinessMapper} style.
 *
 * <p>Extracted from EmployeeService so callers that only need the DTO (whoami, for one) do
 * not have to depend on the whole service.
 */
public class EmployeeMapper {

    private EmployeeMapper() {
    }

    public static EmployeeResponseDTO toDTO(Employee employee) {
        return EmployeeResponseDTO.builder()
                .id(employee.getId())
                .firstName(employee.getFirstName())
                .lastName(employee.getLastName())
                .email(employee.getEmail())
                .phoneNumber(employee.getPhoneNumber())
                .photoUrl(employee.getPhotoUrl())
                .businessId(employee.getBusiness().getId())
                .active(employee.getActive())
                .createdAt(employee.getCreatedAt())
                .updatedAt(employee.getUpdatedAt())
                .build();
    }

    public static List<EmployeeResponseDTO> toDTOList(List<Employee> employees) {
        if (employees == null || employees.isEmpty()) return List.of();
        return employees.stream().map(EmployeeMapper::toDTO).toList();
    }
}
