package com.platform.service;

import com.platform.dto.EmployeeLocationServicePriceRequestDTO;
import com.platform.dto.EmployeeLocationServicePriceResponseDTO;
import com.platform.dto.EmployeeServiceAssignmentRequestDTO;
import com.platform.dto.EmployeeServiceAssignmentResponseDTO;
import com.platform.entity.Business;
import com.platform.entity.Employee;
import com.platform.entity.EmployeeLocationServicePrice;
import com.platform.entity.Location;
import com.platform.entity.ProvidedService;
import com.platform.entity.User;
import com.platform.exception.BadRequestException;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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

    // ==================== assignServicesAtBasePrice (BP-50) ====================

    @Test
    void assignServicesAtBasePrice_createsOneRowPerServicePerLocation() {
        User owner = createBusinessOwner();
        Business business = createBusiness(owner);
        Employee employee = createEmployee(business);
        ProvidedService haircut = createService(business, new BigDecimal("25.00"));
        ProvidedService shave = createService(business, new BigDecimal("15.50"));
        Location downtown = createLocation(business);
        Location uptown = createLocation(business);

        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));
        when(employeeRepository.findById(employee.getId())).thenReturn(Optional.of(employee));
        when(locationRepository.findByBusinessId(business.getId())).thenReturn(List.of(downtown, uptown));
        when(serviceRepository.findById(haircut.getId())).thenReturn(Optional.of(haircut));
        when(serviceRepository.findById(shave.getId())).thenReturn(Optional.of(shave));
        when(priceRepository.findByEmployeeId(employee.getId())).thenReturn(List.of());
        stubSaveAll();

        EmployeeServiceAssignmentResponseDTO response = priceService.assignServicesAtBasePrice(
                business.getId(), employee.getId(),
                new EmployeeServiceAssignmentRequestDTO(List.of(haircut.getId(), shave.getId())),
                owner);

        assertEquals(employee.getId(), response.employeeId());
        assertEquals(4, response.createdCount());
        assertEquals(0, response.alreadyAssignedCount());
        assertEquals(4, response.assignments().size());

        // Every row is priced from the service's own base price, never from the request.
        response.assignments().forEach(a -> {
            BigDecimal expected = a.serviceId().equals(haircut.getId())
                    ? haircut.getPrice() : shave.getPrice();
            assertEquals(expected, a.price());
        });
        assertEquals(Set.of(downtown.getId(), uptown.getId()),
                response.assignments().stream()
                        .map(EmployeeLocationServicePriceResponseDTO::locationId)
                        .collect(Collectors.toSet()));
    }

    @Test
    void assignServicesAtBasePrice_repeatedCall_isIdempotentAndWritesNothing() {
        User owner = createBusinessOwner();
        Business business = createBusiness(owner);
        Employee employee = createEmployee(business);
        ProvidedService service = createService(business, new BigDecimal("25.00"));
        Location location = createLocation(business);
        EmployeeLocationServicePrice alreadyThere = createPrice(employee, service, location);

        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));
        when(employeeRepository.findById(employee.getId())).thenReturn(Optional.of(employee));
        when(locationRepository.findByBusinessId(business.getId())).thenReturn(List.of(location));
        when(serviceRepository.findById(service.getId())).thenReturn(Optional.of(service));
        when(priceRepository.findByEmployeeId(employee.getId())).thenReturn(List.of(alreadyThere));

        EmployeeServiceAssignmentResponseDTO response = priceService.assignServicesAtBasePrice(
                business.getId(), employee.getId(),
                new EmployeeServiceAssignmentRequestDTO(List.of(service.getId())),
                owner);

        // Success, not a 409 - re-assigning is a no-op, not a duplicate-key error.
        assertEquals(0, response.createdCount());
        assertEquals(1, response.alreadyAssignedCount());
        assertEquals(1, response.assignments().size());
        verify(priceRepository, never()).saveAll(any());
        verify(priceRepository, never()).save(any());
    }

    @Test
    void assignServicesAtBasePrice_existingOverride_isNotResetToBasePrice() {
        User owner = createBusinessOwner();
        Business business = createBusiness(owner);
        Employee employee = createEmployee(business);
        ProvidedService service = createService(business, new BigDecimal("25.00"));
        Location location = createLocation(business);

        BigDecimal override = new BigDecimal("99.99");
        EmployeeLocationServicePrice existing = createPrice(employee, service, location);
        existing.setPrice(override);

        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));
        when(employeeRepository.findById(employee.getId())).thenReturn(Optional.of(employee));
        when(locationRepository.findByBusinessId(business.getId())).thenReturn(List.of(location));
        when(serviceRepository.findById(service.getId())).thenReturn(Optional.of(service));
        when(priceRepository.findByEmployeeId(employee.getId())).thenReturn(List.of(existing));

        EmployeeServiceAssignmentResponseDTO response = priceService.assignServicesAtBasePrice(
                business.getId(), employee.getId(),
                new EmployeeServiceAssignmentRequestDTO(List.of(service.getId())),
                owner);

        assertEquals(override, existing.getPrice());
        assertEquals(override, response.assignments().get(0).price());
        verify(priceRepository, never()).saveAll(any());
    }

    @Test
    void assignServicesAtBasePrice_partiallyAssigned_createsOnlyTheMissingRows() {
        User owner = createBusinessOwner();
        Business business = createBusiness(owner);
        Employee employee = createEmployee(business);
        ProvidedService service = createService(business, new BigDecimal("25.00"));
        Location downtown = createLocation(business);
        Location uptown = createLocation(business);
        EmployeeLocationServicePrice existingAtDowntown = createPrice(employee, service, downtown);

        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));
        when(employeeRepository.findById(employee.getId())).thenReturn(Optional.of(employee));
        when(locationRepository.findByBusinessId(business.getId())).thenReturn(List.of(downtown, uptown));
        when(serviceRepository.findById(service.getId())).thenReturn(Optional.of(service));
        when(priceRepository.findByEmployeeId(employee.getId())).thenReturn(List.of(existingAtDowntown));
        stubSaveAll();

        EmployeeServiceAssignmentResponseDTO response = priceService.assignServicesAtBasePrice(
                business.getId(), employee.getId(),
                new EmployeeServiceAssignmentRequestDTO(List.of(service.getId())),
                owner);

        assertEquals(1, response.createdCount());
        assertEquals(1, response.alreadyAssignedCount());
        assertEquals(uptown.getId(), response.assignments().stream()
                .filter(a -> !a.locationId().equals(downtown.getId()))
                .findFirst().orElseThrow().locationId());
    }

    @Test
    void assignServicesAtBasePrice_duplicateServiceIdInBody_createsOneRowPerLocation() {
        User owner = createBusinessOwner();
        Business business = createBusiness(owner);
        Employee employee = createEmployee(business);
        ProvidedService service = createService(business, new BigDecimal("25.00"));
        Location location = createLocation(business);

        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));
        when(employeeRepository.findById(employee.getId())).thenReturn(Optional.of(employee));
        when(locationRepository.findByBusinessId(business.getId())).thenReturn(List.of(location));
        when(serviceRepository.findById(service.getId())).thenReturn(Optional.of(service));
        when(priceRepository.findByEmployeeId(employee.getId())).thenReturn(List.of());
        stubSaveAll();

        EmployeeServiceAssignmentResponseDTO response = priceService.assignServicesAtBasePrice(
                business.getId(), employee.getId(),
                new EmployeeServiceAssignmentRequestDTO(
                        List.of(service.getId(), service.getId(), service.getId())),
                owner);

        assertEquals(1, response.createdCount());
    }

    @Test
    void assignServicesAtBasePrice_notOwner_throwsBusinessException() {
        User owner = createBusinessOwner();
        User otherUser = createOtherUser();
        Business business = createBusiness(owner);

        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));

        assertThrows(BusinessException.class,
                () -> priceService.assignServicesAtBasePrice(business.getId(), UUID.randomUUID(),
                        new EmployeeServiceAssignmentRequestDTO(List.of(UUID.randomUUID())), otherUser));

        verify(priceRepository, never()).saveAll(any());
    }

    @Test
    void assignServicesAtBasePrice_serviceFromAnotherBusiness_throwsBusinessException() {
        User owner = createBusinessOwner();
        Business business = createBusiness(owner);
        Business otherBusiness = createBusiness(owner);
        Employee employee = createEmployee(business);
        Location location = createLocation(business);
        ProvidedService foreignService = createService(otherBusiness, new BigDecimal("25.00"));

        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));
        when(employeeRepository.findById(employee.getId())).thenReturn(Optional.of(employee));
        when(locationRepository.findByBusinessId(business.getId())).thenReturn(List.of(location));
        when(serviceRepository.findById(foreignService.getId())).thenReturn(Optional.of(foreignService));

        assertThrows(BusinessException.class,
                () -> priceService.assignServicesAtBasePrice(business.getId(), employee.getId(),
                        new EmployeeServiceAssignmentRequestDTO(List.of(foreignService.getId())), owner));

        verify(priceRepository, never()).saveAll(any());
    }

    @Test
    void assignServicesAtBasePrice_unknownEmployee_throwsNotFound() {
        User owner = createBusinessOwner();
        Business business = createBusiness(owner);
        UUID unknownEmployeeId = UUID.randomUUID();

        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));
        when(employeeRepository.findById(unknownEmployeeId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> priceService.assignServicesAtBasePrice(business.getId(), unknownEmployeeId,
                        new EmployeeServiceAssignmentRequestDTO(List.of(UUID.randomUUID())), owner));

        verify(priceRepository, never()).saveAll(any());
    }

    @Test
    void assignServicesAtBasePrice_unknownService_throwsNotFound() {
        User owner = createBusinessOwner();
        Business business = createBusiness(owner);
        Employee employee = createEmployee(business);
        Location location = createLocation(business);
        UUID unknownServiceId = UUID.randomUUID();

        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));
        when(employeeRepository.findById(employee.getId())).thenReturn(Optional.of(employee));
        when(locationRepository.findByBusinessId(business.getId())).thenReturn(List.of(location));
        when(serviceRepository.findById(unknownServiceId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> priceService.assignServicesAtBasePrice(business.getId(), employee.getId(),
                        new EmployeeServiceAssignmentRequestDTO(List.of(unknownServiceId)), owner));

        verify(priceRepository, never()).saveAll(any());
    }

    @Test
    void assignServicesAtBasePrice_businessWithNoLocations_throwsBadRequest() {
        User owner = createBusinessOwner();
        Business business = createBusiness(owner);
        Employee employee = createEmployee(business);

        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));
        when(employeeRepository.findById(employee.getId())).thenReturn(Optional.of(employee));
        when(locationRepository.findByBusinessId(business.getId())).thenReturn(List.of());

        // A no-op would leave the owner with an employee nobody can book and no explanation.
        assertThrows(BadRequestException.class,
                () -> priceService.assignServicesAtBasePrice(business.getId(), employee.getId(),
                        new EmployeeServiceAssignmentRequestDTO(List.of(UUID.randomUUID())), owner));

        verify(priceRepository, never()).saveAll(any());
    }

    @Test
    void assignServicesAtBasePrice_unknownBusiness_throwsNotFound() {
        User owner = createBusinessOwner();
        UUID unknownBusinessId = UUID.randomUUID();

        when(businessRepository.findById(unknownBusinessId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> priceService.assignServicesAtBasePrice(unknownBusinessId, UUID.randomUUID(),
                        new EmployeeServiceAssignmentRequestDTO(List.of(UUID.randomUUID())), owner));

        verify(priceRepository, never()).saveAll(any());
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
        return createService(business, BigDecimal.TEN);
    }

    private ProvidedService createService(Business business, BigDecimal basePrice) {
        return ProvidedService.builder()
                .id(UUID.randomUUID())
                .name("Haircut")
                .price(basePrice)
                .business(business)
                .build();
    }

    /** {@code saveAll} echoes the rows back with ids assigned, as JPA would. */
    private void stubSaveAll() {
        when(priceRepository.saveAll(any())).thenAnswer(i -> {
            List<EmployeeLocationServicePrice> rows = i.getArgument(0);
            rows.forEach(row -> row.setId(UUID.randomUUID()));
            return rows;
        });
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
