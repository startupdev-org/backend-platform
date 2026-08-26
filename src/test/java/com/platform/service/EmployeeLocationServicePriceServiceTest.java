package com.platform.service;

import com.platform.dto.EmployeeLocationServicePriceRequestDTO;
import com.platform.dto.EmployeeLocationServicePriceResponseDTO;
import com.platform.entity.Business;
import com.platform.entity.Employee;
import com.platform.entity.EmployeeLocationServicePrice;
import com.platform.entity.Location;
import com.platform.entity.ProvidedService;
import com.platform.entity.User;
import com.platform.exception.BusinessException;
import com.platform.exception.ResourceNotFoundException;
import com.platform.repository.BusinessRepository;
import com.platform.repository.EmployeeLocationServicePriceRepository;
import com.platform.repository.EmployeeRepository;
import com.platform.repository.LocationRepository;
import com.platform.repository.ServiceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeLocationServicePriceServiceTest {

    @Mock
    private EmployeeLocationServicePriceRepository priceRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private BusinessRepository businessRepository;

    @InjectMocks
    private EmployeeLocationServicePriceService priceService;

    // ==================== create ====================

    @Test
    void create_success() {
        User owner = createBusinessOwner();
        Business business = createBusiness(owner);
        Employee employee = createEmployee(business);
        ProvidedService service = createService(business);
        Location location = createLocation(business);
        EmployeeLocationServicePriceRequestDTO dto = new EmployeeLocationServicePriceRequestDTO(
                employee.getId(), service.getId(), location.getId(), BigDecimal.TEN);

        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));
        when(priceRepository.existsByEmployeeIdAndServiceIdAndLocationId(
                employee.getId(), service.getId(), location.getId())).thenReturn(false);
        when(employeeRepository.findById(employee.getId())).thenReturn(Optional.of(employee));
        when(serviceRepository.findById(service.getId())).thenReturn(Optional.of(service));
        when(locationRepository.findById(location.getId())).thenReturn(Optional.of(location));
        when(priceRepository.save(any())).thenAnswer(i -> {
            EmployeeLocationServicePrice p = i.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        EmployeeLocationServicePriceResponseDTO response = priceService.create(business.getId(), dto, owner);

        assertNotNull(response);
        assertEquals(employee.getId(), response.employeeId());
        assertEquals(location.getId(), response.locationId());
        verify(priceRepository).save(any());
    }

    @Test
    void create_notOwner_throwsBusinessException() {
        User owner = createBusinessOwner();
        User otherUser = createOtherUser();
        Business business = createBusiness(owner);
        EmployeeLocationServicePriceRequestDTO dto = new EmployeeLocationServicePriceRequestDTO(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN);

        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));

        assertThrows(BusinessException.class,
                () -> priceService.create(business.getId(), dto, otherUser));

        verify(priceRepository, never()).save(any());
    }

    @Test
    void create_crossBusinessMismatch_throwsBusinessException() {
        User owner = createBusinessOwner();
        Business business = createBusiness(owner);
        Business otherBusiness = createBusiness(owner);
        Employee employee = createEmployee(business);
        ProvidedService service = createService(business);
        Location locationFromOtherBusiness = createLocation(otherBusiness);
        EmployeeLocationServicePriceRequestDTO dto = new EmployeeLocationServicePriceRequestDTO(
                employee.getId(), service.getId(), locationFromOtherBusiness.getId(), BigDecimal.TEN);

        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));
        when(priceRepository.existsByEmployeeIdAndServiceIdAndLocationId(
                employee.getId(), service.getId(), locationFromOtherBusiness.getId())).thenReturn(false);
        when(employeeRepository.findById(employee.getId())).thenReturn(Optional.of(employee));
        when(serviceRepository.findById(service.getId())).thenReturn(Optional.of(service));
        when(locationRepository.findById(locationFromOtherBusiness.getId())).thenReturn(Optional.of(locationFromOtherBusiness));

        assertThrows(BusinessException.class,
                () -> priceService.create(business.getId(), dto, owner));

        verify(priceRepository, never()).save(any());
    }

    // ==================== delete ====================

    @Test
    void delete_success() {
        User owner = createBusinessOwner();
        Business business = createBusiness(owner);
        Employee employee = createEmployee(business);
        ProvidedService service = createService(business);
        Location location = createLocation(business);
        EmployeeLocationServicePrice entity = createPrice(employee, service, location);

        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));
        when(priceRepository.findById(entity.getId())).thenReturn(Optional.of(entity));

        priceService.delete(business.getId(), entity.getId(), owner);

        verify(priceRepository).delete(entity);
    }

    @Test
    void delete_belongsToDifferentBusiness_throwsNotFound() {
        User owner = createBusinessOwner();
        Business business = createBusiness(owner);
        Business otherBusiness = createBusiness(owner);
        Employee employee = createEmployee(otherBusiness);
        ProvidedService service = createService(otherBusiness);
        Location location = createLocation(otherBusiness);
        EmployeeLocationServicePrice entity = createPrice(employee, service, location);

        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));
        when(priceRepository.findById(entity.getId())).thenReturn(Optional.of(entity));

        assertThrows(ResourceNotFoundException.class,
                () -> priceService.delete(business.getId(), entity.getId(), owner));

        verify(priceRepository, never()).delete(any());
    }

    @Test
    void delete_notOwner_throwsBusinessException() {
        User owner = createBusinessOwner();
        User otherUser = createOtherUser();
        Business business = createBusiness(owner);
        UUID priceId = UUID.randomUUID();

        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));

        assertThrows(BusinessException.class,
                () -> priceService.delete(business.getId(), priceId, otherUser));

        verify(priceRepository, never()).delete(any());
    }

    // ==================== Helpers ====================

    private User createBusinessOwner() {
        return User.builder().id(UUID.randomUUID()).email("test@gmail.com").role(User.UserRole.BUSINESS_ADMIN).build();
    }

    private User createOtherUser() {
        return User.builder().id(UUID.randomUUID()).email("other@gmail.com").role(User.UserRole.BUSINESS_ADMIN).build();
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

    private Employee createEmployee(Business business) {
        return Employee.builder()
                .id(UUID.randomUUID())
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .enabled(true)
                .business(business)
                .build();
    }

    private ProvidedService createService(Business business) {
        return ProvidedService.builder()
                .id(UUID.randomUUID())
                .name("Haircut")
                .business(business)
                .build();
    }

    private Location createLocation(Business business) {
        return Location.builder()
                .id(UUID.randomUUID())
                .business(business)
                .name("Main Location")
                .isDefaultLocation(true)
                .build();
    }

    private EmployeeLocationServicePrice createPrice(Employee employee, ProvidedService service, Location location) {
        return EmployeeLocationServicePrice.builder()
                .id(UUID.randomUUID())
                .employee(employee)
                .service(service)
                .location(location)
                .price(BigDecimal.TEN)
                .build();
    }
}
