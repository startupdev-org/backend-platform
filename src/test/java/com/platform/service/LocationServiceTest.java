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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private BusinessRepository businessRepository;

    @InjectMocks
    private LocationService locationService;

    // ==================== createLocation ====================

    @Test
    void createLocation_success() {
        User owner = createBusinessOwner();
        Business business = createBusiness(owner);
        LocationRequestDTO request = createLocationRequest();

        when(businessRepository.findById(business.getId()))
                .thenReturn(Optional.of(business));

        when(locationRepository.save(any()))
                .thenAnswer(i -> {
                    Location l = i.getArgument(0);
                    l.setId(UUID.randomUUID());
                    return l;
                });

        LocationResponseDTO response = locationService.createLocation(business.getId(), request, owner);

        assertNotNull(response);
        assertEquals(business.getId(), response.getBusinessId());
        assertEquals(request.getName(), response.getName());
        verify(locationRepository).save(any());
    }

    @Test
    void createLocation_businessNotFound() {
        User owner = createBusinessOwner();
        UUID businessId = UUID.randomUUID();
        LocationRequestDTO request = createLocationRequest();

        when(businessRepository.findById(businessId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> locationService.createLocation(businessId, request, owner));

        verify(locationRepository, never()).save(any());
    }

    @Test
    void createLocation_notOwner_throwsBusinessException() {
        User owner = createBusinessOwner();
        User otherUser = createOtherUser();
        Business business = createBusiness(owner);
        LocationRequestDTO request = createLocationRequest();

        when(businessRepository.findById(business.getId()))
                .thenReturn(Optional.of(business));

        assertThrows(BusinessException.class,
                () -> locationService.createLocation(business.getId(), request, otherUser));

        verify(locationRepository, never()).save(any());
    }

    // ==================== getLocationsForBusiness ====================

    @Test
    void getLocationsForBusiness_success() {
        User owner = createBusinessOwner();
        Business business = createBusiness(owner);
        Location location = createLocation(business);

        when(locationRepository.findByBusinessId(business.getId()))
                .thenReturn(List.of(location));

        List<LocationResponseDTO> response = locationService.getLocationsForBusiness(business.getId());

        assertNotNull(response);
        assertEquals(1, response.size());
        assertEquals(location.getId(), response.get(0).getId());
    }

    // ==================== getLocationById ====================

    @Test
    void getLocationById_success() {
        User owner = createBusinessOwner();
        Business business = createBusiness(owner);
        Location location = createLocation(business);

        when(locationRepository.findById(location.getId()))
                .thenReturn(Optional.of(location));

        LocationResponseDTO response = locationService.getLocationById(business.getId(), location.getId());

        assertNotNull(response);
        assertEquals(location.getId(), response.getId());
    }

    @Test
    void getLocationById_notFound() {
        UUID businessId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();

        when(locationRepository.findById(locationId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> locationService.getLocationById(businessId, locationId));
    }

    @Test
    void getLocationById_belongsToDifferentBusiness_throwsNotFound() {
        User owner = createBusinessOwner();
        Business business = createBusiness(owner);
        Location location = createLocation(business);
        UUID otherBusinessId = UUID.randomUUID();

        when(locationRepository.findById(location.getId()))
                .thenReturn(Optional.of(location));

        assertThrows(ResourceNotFoundException.class,
                () -> locationService.getLocationById(otherBusinessId, location.getId()));
    }

    // ==================== updateLocation ====================

    @Test
    void updateLocation_success() {
        User owner = createBusinessOwner();
        Business business = createBusiness(owner);
        Location location = createLocation(business);
        LocationRequestDTO request = createLocationRequest();
        request.setName("Updated Name");

        when(businessRepository.findById(business.getId()))
                .thenReturn(Optional.of(business));

        when(locationRepository.findById(location.getId()))
                .thenReturn(Optional.of(location));

        when(locationRepository.save(any()))
                .thenAnswer(i -> i.getArgument(0));

        LocationResponseDTO response = locationService.updateLocation(
                business.getId(), location.getId(), request, owner);

        assertNotNull(response);
        assertEquals("Updated Name", response.getName());
        assertEquals(business.getId(), response.getBusinessId());
        verify(locationRepository).save(any());
    }

    @Test
    void updateLocation_notOwner_throwsBusinessException() {
        User owner = createBusinessOwner();
        User otherUser = createOtherUser();
        Business business = createBusiness(owner);
        UUID locationId = UUID.randomUUID();
        LocationRequestDTO request = createLocationRequest();

        when(businessRepository.findById(business.getId()))
                .thenReturn(Optional.of(business));

        assertThrows(BusinessException.class,
                () -> locationService.updateLocation(business.getId(), locationId, request, otherUser));

        verify(locationRepository, never()).save(any());
    }

    @Test
    void updateLocation_belongsToDifferentBusiness_throwsNotFound() {
        User owner = createBusinessOwner();
        Business business = createBusiness(owner);
        Business otherBusiness = createBusiness(owner);
        Location location = createLocation(otherBusiness);
        LocationRequestDTO request = createLocationRequest();

        when(businessRepository.findById(business.getId()))
                .thenReturn(Optional.of(business));

        when(locationRepository.findById(location.getId()))
                .thenReturn(Optional.of(location));

        assertThrows(ResourceNotFoundException.class,
                () -> locationService.updateLocation(business.getId(), location.getId(), request, owner));

        verify(locationRepository, never()).save(any());
    }

    // ==================== deleteLocation ====================

    @Test
    void deleteLocation_success() {
        User owner = createBusinessOwner();
        Business business = createBusiness(owner);
        Location location = createLocation(business);

        when(businessRepository.findById(business.getId()))
                .thenReturn(Optional.of(business));

        when(locationRepository.findById(location.getId()))
                .thenReturn(Optional.of(location));

        locationService.deleteLocation(business.getId(), location.getId(), owner);

        verify(locationRepository).delete(location);
    }

    @Test
    void deleteLocation_notOwner_throwsBusinessException() {
        User owner = createBusinessOwner();
        User otherUser = createOtherUser();
        Business business = createBusiness(owner);
        UUID locationId = UUID.randomUUID();

        when(businessRepository.findById(business.getId()))
                .thenReturn(Optional.of(business));

        assertThrows(BusinessException.class,
                () -> locationService.deleteLocation(business.getId(), locationId, otherUser));

        verify(locationRepository, never()).delete(any());
    }

    @Test
    void deleteLocation_businessNotFound() {
        User owner = createBusinessOwner();
        UUID businessId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();

        when(businessRepository.findById(businessId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> locationService.deleteLocation(businessId, locationId, owner));

        verify(locationRepository, never()).delete(any());
    }

    // ==================== Helpers ====================

    private User createBusinessOwner() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("test@gmail.com")
                .role(User.UserRole.BUSINESS_ADMIN)
                .build();
    }

    private User createOtherUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("other@gmail.com")
                .role(User.UserRole.BUSINESS_ADMIN)
                .build();
    }

    private Business createBusiness(User owner) {
        return Business.builder()
                .id(UUID.randomUUID())
                .name("Test Business")
                .slug("test-business-" + UUID.randomUUID())
                .address("123 Test Street")
                .city("Test City")
                .phone("+1234567890")
                .owner(owner)
                .build();
    }

    private Location createLocation(Business business) {
        return Location.builder()
                .id(UUID.randomUUID())
                .business(business)
                .name("Main Location")
                .address("456 Location Ave")
                .city("Test City")
                .country("Moldova")
                .isDefaultLocation(true)
                .build();
    }

    private LocationRequestDTO createLocationRequest() {
        LocationRequestDTO dto = new LocationRequestDTO();
        dto.setName("Main Location");
        dto.setAddress("456 Location Ave");
        dto.setCity("Test City");
        dto.setCountry("Moldova");
        dto.setIsDefaultLocation(true);
        return dto;
    }
}
