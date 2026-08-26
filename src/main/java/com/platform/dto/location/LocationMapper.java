package com.platform.dto.location;
import com.platform.entity.Location;

import java.util.List;

/**
 * Location -> DTO mapping, mirroring the existing {@code BusinessMapper} style.
 *
 * <p>Extracted from LocationService so callers that only need the DTO (whoami, for one) do
 * not have to depend on the whole service.
 */
public class LocationMapper {

    private LocationMapper() {
    }

    public static LocationResponseDTO toDTO(Location location) {
        return LocationResponseDTO.builder()
                .id(location.getId())
                .businessId(location.getBusiness().getId())
                .name(location.getName())
                .address(location.getAddress())
                .city(location.getCity())
                .country(location.getCountry())
                .latitude(location.getLatitude())
                .longitude(location.getLongitude())
                .isDefaultLocation(location.getIsDefaultLocation())
                .build();
    }

    public static List<LocationResponseDTO> toDTOList(List<Location> locations) {
        if (locations == null || locations.isEmpty()) return List.of();
        return locations.stream().map(com.platform.dto.location.LocationMapper::toDTO).toList();
    }
}
