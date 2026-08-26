package com.platform.dto.employee;

import com.platform.entity.Employee;
import com.platform.storage.ImageUrlResolver;

import java.util.List;

/**
 * Employee -> DTO mapping, mirroring the existing {@code BusinessMapper} style.
 *
 * <p>Extracted from EmployeeService so callers that only need the DTO (whoami, for one) do
 * not have to depend on the whole service.
 *
 * <p>The resolver is passed in rather than injected so this stays a plain static mapper
 * with no Spring dependency, matching the rest of the dto package.
 */
public class EmployeeMapper {

    private EmployeeMapper() {
    }

    public static EmployeeResponseDTO toDTO(Employee employee, ImageUrlResolver imageUrls) {
        return EmployeeResponseDTO.builder()
                .id(employee.getId())
                .firstName(employee.getFirstName())
                .lastName(employee.getLastName())
                .email(employee.getEmail())
                .phoneNumber(employee.getPhoneNumber())
                .photoUrl(imageUrls.toPublicUrl(employee.getPhotoKey()))
                .businessId(employee.getBusiness().getId())
                .enabled(employee.getEnabled())
                .createdAt(employee.getCreatedAt())
                .updatedAt(employee.getUpdatedAt())
                .build();
    }

    public static List<EmployeeResponseDTO> toDTOList(List<Employee> employees, ImageUrlResolver imageUrls) {
        if (employees == null || employees.isEmpty()) return List.of();
        return employees.stream().map(employee -> toDTO(employee, imageUrls)).toList();
    }
}
