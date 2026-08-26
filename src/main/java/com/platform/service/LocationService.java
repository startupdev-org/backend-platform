package com.platform.service;

import com.platform.dto.location.LocationRequestDTO;
import com.platform.dto.location.LocationResponseDTO;
import com.platform.entity.Business;
import com.platform.entity.Location;
import com.platform.entity.User;
import com.platform.exception.BusinessException;
import com.platform.exception.ResourceNotFoundException;
import com.platform.repository.BusinessRepository;
import com.platform.repository.LocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepository locationRepository;
    private final BusinessRepository businessRepository;

    private static final String BUSINESS_NOT_FOUND = "Business not found";
    private static final String LOCATION_NOT_FOUND = "Location not found";

    // Create a location
    public LocationResponseDTO createLocation(UUID businessId, LocationRequestDTO dto, User currentUser) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException(BUSINESS_NOT_FOUND));

        validateBusinessOwnership(business, currentUser);

        Location location = Location.builder()
                .business(business)
                .name(dto.getName())
                .address(dto.getAddress())
                .city(dto.getCity())
                .country(dto.getCountry())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .isDefaultLocation(dto.getIsDefaultLocation() != null ? dto.getIsDefaultLocation() : false)
                .build();

        Location saved = locationRepository.save(location);
        return mapToDTO(saved);
    }

    // Get all locations for a business
    public List<LocationResponseDTO> getLocationsForBusiness(UUID businessId) {
        return locationRepository.findByBusinessId(businessId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    // Get location by ID, scoped to a business
    public LocationResponseDTO getLocationById(UUID businessId, UUID locationId) {
        Location location = getLocationForBusiness(businessId, locationId);
        return mapToDTO(location);
    }

    // Update location
    public LocationResponseDTO updateLocation(UUID businessId, UUID locationId, LocationRequestDTO dto, User currentUser) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException(BUSINESS_NOT_FOUND));

        validateBusinessOwnership(business, currentUser);

        Location location = getLocationForBusiness(businessId, locationId);

        if (dto.getName() != null) location.setName(dto.getName());
        if (dto.getAddress() != null) location.setAddress(dto.getAddress());
        if (dto.getCity() != null) location.setCity(dto.getCity());
        if (dto.getCountry() != null) location.setCountry(dto.getCountry());
        if (dto.getLatitude() != null) location.setLatitude(dto.getLatitude());
        if (dto.getLongitude() != null) location.setLongitude(dto.getLongitude());
        if (dto.getIsDefaultLocation() != null) location.setIsDefaultLocation(dto.getIsDefaultLocation());

        Location updated = locationRepository.save(location);
        return mapToDTO(updated);
    }

    // Delete location
    public void deleteLocation(UUID businessId, UUID locationId, User currentUser) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException(BUSINESS_NOT_FOUND));

        validateBusinessOwnership(business, currentUser);

        Location location = getLocationForBusiness(businessId, locationId);
        locationRepository.delete(location);
    }

    private Location getLocationForBusiness(UUID businessId, UUID locationId) {
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new ResourceNotFoundException(LOCATION_NOT_FOUND));

        if (!location.getBusiness().getId().equals(businessId)) {
            throw new ResourceNotFoundException(LOCATION_NOT_FOUND);
        }

        return location;
    }

    private void validateBusinessOwnership(Business business, User currentUser) {
        if (business.isNotOwner(currentUser) &&
                !currentUser.getRole().equals(User.UserRole.PLATFORM_ADMIN)) {
            throw new BusinessException("Unauthorized");
        }
    }

    // Mapper
    private LocationResponseDTO mapToDTO(Location location) {
        return LocationResponseDTO.builder()
                .id(location.getId())
                .businessId(location.getBusiness() != null ? location.getBusiness().getId() : null)
                .name(location.getName())
                .address(location.getAddress())
                .city(location.getCity())
                .country(location.getCountry())
                .latitude(location.getLatitude())
                .longitude(location.getLongitude())
                .isDefaultLocation(location.getIsDefaultLocation())
                .build();
    }
}
