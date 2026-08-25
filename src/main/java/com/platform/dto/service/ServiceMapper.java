package com.platform.dto.service;

import com.platform.entity.ProvidedService;

import java.util.List;

/**
 * ProvidedService -> DTO mapping, mirroring the existing {@code BusinessMapper} style.
 */
public class ServiceMapper {

    private ServiceMapper() {
    }

    public static ServiceResponseDTO toDTO(ProvidedService providedService) {
        return ServiceResponseDTO.builder()
                .id(providedService.getId())
                .name(providedService.getName())
                .description(providedService.getDescription())
                .price(providedService.getPrice())
                .durationMinutes(providedService.getDurationMinutes())
                .businessId(providedService.getBusiness().getId())
                .active(providedService.getActive())
                .createdAt(providedService.getCreatedAt())
                .updatedAt(providedService.getUpdatedAt())
                .build();
    }

    public static List<ServiceResponseDTO> toDTOList(List<ProvidedService> services) {
        if (services == null || services.isEmpty()) return List.of();
        return services.stream().map(ServiceMapper::toDTO).toList();
    }
}
