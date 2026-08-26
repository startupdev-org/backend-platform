package com.platform.dto.business;

import com.platform.dto.employee.EmployeeResponseDTO;
import com.platform.dto.location.LocationResponseDTO;
import com.platform.dto.service.ServiceResponseDTO;
import com.platform.dto.user.UserResponseDTO;
import com.platform.entity.Business;
import com.platform.entity.BusinessWorkingHours;
import com.platform.entity.ProvidedService;
import com.platform.entity.User;
import com.platform.storage.ImageUrlResolver;

import java.util.List;
import java.util.Set;

public class BusinessMapper {

    public static BusinessWorkingHoursDTO toDTO(BusinessWorkingHours wh) {
        return BusinessWorkingHoursDTO.builder()
                .id(wh.getId())
                .dayOfWeek(wh.getDayOfWeek())
                .openTime(wh.getOpenTime())
                .closeTime(wh.getCloseTime())
                .build();
    }


    public static BusinessWorkingHours toEntity(BusinessWorkingHoursDTO dto) {
        BusinessWorkingHours entity = new BusinessWorkingHours();
        entity.setDayOfWeek(dto.getDayOfWeek());
        entity.setOpenTime(dto.getOpenTime());
        entity.setCloseTime(dto.getCloseTime());
        return entity;
    }

    public static List<BusinessWorkingHoursDTO> toWorkingHoursDTOList(List<BusinessWorkingHours> hours) {
        if (hours == null || hours.isEmpty()) return List.of();
        return hours.stream().map(BusinessMapper::toDTO).toList();
    }

    public static List<BusinessWorkingHours> fromWorkingHoursDTOList(List<BusinessWorkingHoursDTO> hours) {
        if (hours == null || hours.isEmpty()) return List.of();
        return hours.stream().map(BusinessMapper::toEntity).toList();
    }

    public static BusinessResponseDTO toDTO(
            Business business,
            List<ServiceResponseDTO> services,
            List<EmployeeResponseDTO> employeeList,
            Set<BusinessFeatureDTO> features,
            User owner,
            ImageUrlResolver imageUrls
    ) {
        return BusinessResponseDTO.builder()
                .id(business.getId())
                .name(business.getName())
                .slug(business.getSlug())
                .description(business.getDescription())
                .address(business.getAddress())
                .city(business.getCity())
                .phone(business.getPhone())
                .website(business.getWebsite())
                .logoUrl(imageUrls.toPublicUrl(business.getLogoKey()))
                .coverImageUrl(imageUrls.toPublicUrl(business.getCoverImageKey()))
                .ratingOverall(business.getRatingOverall() != null ? business.getRatingOverall() : 0.0)
                .createdAt(business.getCreatedAt())
                .updatedAt(business.getUpdatedAt())
                .owner(owner != null ? toDTO(owner) : null)
                .businessWorkingHours(toWorkingHoursDTOList(business.getWorkingHours()))
                .providedServices(services)
                .employeeList(employeeList)
                .businessFeatures(features)
                .build();
    }

    public static BusinessResponseDTO toDTO(
            Business business,
            List<ServiceResponseDTO> services,
            List<EmployeeResponseDTO> employeeList,
            Set<BusinessFeatureDTO> features,
            List<LocationResponseDTO> locationList,
            User owner,
            ImageUrlResolver imageUrls
    ) {
        return BusinessResponseDTO.builder()
                .id(business.getId())
                .name(business.getName())
                .slug(business.getSlug())
                .description(business.getDescription())
                .address(business.getAddress())
                .city(business.getCity())
                .phone(business.getPhone())
                .website(business.getWebsite())
                .logoUrl(imageUrls.toPublicUrl(business.getLogoKey()))
                .coverImageUrl(imageUrls.toPublicUrl(business.getCoverImageKey()))
                .ratingOverall(business.getRatingOverall() != null ? business.getRatingOverall() : 0.0)
                .createdAt(business.getCreatedAt())
                .updatedAt(business.getUpdatedAt())
                .owner(owner != null ? toDTO(owner) : null)
                .businessWorkingHours(toWorkingHoursDTOList(business.getWorkingHours()))
                .providedServices(services)
                .employeeList(employeeList)
                .businessFeatures(features)
                .locationList(locationList)
                .build();
    }

    public static Business toEntity(BusinessResponseDTO businessDTO, List<ProvidedService> services) {
        return Business.builder()
                .id(businessDTO.getId())
                .name(businessDTO.getName())
                .slug(businessDTO.getSlug())
                .description(businessDTO.getDescription())
                .address(businessDTO.getAddress())
                .city(businessDTO.getCity())
                .phone(businessDTO.getPhone())
                .website(businessDTO.getWebsite())
                .ratingOverall(businessDTO.getRatingOverall())
                .createdAt(businessDTO.getCreatedAt())
                .updatedAt(businessDTO.getUpdatedAt())
                .workingHours(fromWorkingHoursDTOList(businessDTO.getBusinessWorkingHours()))
                .providedServices(services)
                .build();
    }

    public static UserResponseDTO toDTO(User user) {
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
