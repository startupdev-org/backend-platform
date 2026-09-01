package com.platform.dto.business;

import com.platform.dto.employee.EmployeeResponseDTO;
import com.platform.dto.location.LocationResponseDTO;
import com.platform.dto.service.ServiceResponseDTO;
import com.platform.dto.user.UserResponseDTO;
import com.platform.entity.Employee;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessResponseDTO {

    private UUID id;
    private String name;
    private String slug;
    private String description;
    private String address;
    private String city;
    private String phone;
    private String website;
    private String logoUrl;
    private String coverImageUrl;
    private UserResponseDTO owner;
    private Double ratingOverall;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<ServiceResponseDTO> providedServices;
    private List<EmployeeResponseDTO> employeeList;
    private List<BusinessWorkingHoursDTO> businessWorkingHours;
    private Set<BusinessFeatureDTO> businessFeatures;
    private List<LocationResponseDTO> locationList;
}
