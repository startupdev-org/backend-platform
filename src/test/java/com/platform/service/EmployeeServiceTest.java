package com.platform.service;

import com.platform.dto.employee.EmployeeRequestDTO;
import com.platform.dto.employee.EmployeeResponseDTO;
import com.platform.entity.Business;
import com.platform.entity.Employee;
import com.platform.entity.User;
import com.platform.exception.BusinessException;
import com.platform.exception.ResourceNotFoundException;
import com.platform.repository.BusinessRepository;
import com.platform.repository.EmployeeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private EmployeeService employeeService;

    private static final String TEST_EMAIL = "test@gmail.com";

    @BeforeEach
    void setUp() {
        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.getPrincipal()).thenReturn(TEST_EMAIL);
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ==================== createEmployee ====================

    @Test
    void createEmployee_success() {
        User owner = createBusinessOwner();
        Business business = createBusiness(owner);
        EmployeeRequestDTO request = createEmployeeRequest();

        when(businessRepository.findById(business.getId()))
                .thenReturn(Optional.of(business));

        when(userService.getUserByUsername(TEST_EMAIL))
                .thenReturn(owner);

        when(employeeRepository.save(any()))
                .thenAnswer(i -> {
                    Employee e = i.getArgument(0);
                    e.setId(UUID.randomUUID());
                    e.setCreatedAt(LocalDateTime.now());
                    e.setUpdatedAt(LocalDateTime.now());
                    return e;
                });

        EmployeeResponseDTO response = employeeService.createEmployee(business.getId(), request);

        assertNotNull(response);
        assertEquals(request.getFirstName(), response.getFirstName());
        assertEquals(request.getLastName(), response.getLastName());
        assertEquals(request.getEmail(), response.getEmail());
        assertEquals(request.getPhoneNumber(), response.getPhoneNumber());
        assertEquals(request.getPhotoUrl(), response.getPhotoUrl());
        assertTrue(response.getActive());
        verify(employeeRepository).save(any());
    }

    @Test
    void createEmployee_businessNotFound() {
        EmployeeRequestDTO request = createEmployeeRequest();
        UUID businessId = UUID.randomUUID();

        when(businessRepository.findById(businessId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> employeeService.createEmployee(businessId, request));

        verify(employeeRepository, never()).save(any());
    }

    @Test
    void createEmployee_notOwner_throwsBusinessException() {
        User owner = createBusinessOwner();
        User otherUser = createOtherUser();
        Business business = createBusiness(owner);
        EmployeeRequestDTO request = createEmployeeRequest();

        when(businessRepository.findById(business.getId()))
                .thenReturn(Optional.of(business));

        when(userService.getUserByUsername(TEST_EMAIL))
                .thenReturn(otherUser);

        assertThrows(BusinessException.class,
                () -> employeeService.createEmployee(business.getId(), request));

        verify(employeeRepository, never()).save(any());
    }

    // ==================== getEmployee ====================

    @Test
    void getEmployee_success() {
        User owner = createBusinessOwner();
        Business business = createBusiness(owner);
        Employee employee = createEmployee(business);

        when(employeeRepository.findById(employee.getId()))
                .thenReturn(Optional.of(employee));

        EmployeeResponseDTO response = employeeService.getEmployee(employee.getId());

        assertNotNull(response);
        assertEquals(employee.getId(), response.getId());
        assertEquals(employee.getFirstName(), response.getFirstName());
        assertEquals(employee.getLastName(), response.getLastName());
    }

    @Test
    void getEmployee_notFound() {
        UUID employeeId = UUID.randomUUID();

        when(employeeRepository.findById(employeeId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> employeeService.getEmployee(employeeId));
    }

    // ==================== getBusinessEmployeesList ====================

    @Test
    void getBusinessEmployeesList_success() {
        User owner = createBusinessOwner();
        Business business = createBusiness(owner);
        Employee employee = createEmployee(business);

        when(employeeRepository.findByBusinessId(business.getId()))
                .thenReturn(List.of(employee));

        List<EmployeeResponseDTO> response = employeeService.getBusinessEmployeesList(business.getId());

        assertNotNull(response);
        assertEquals(1, response.size());
        assertEquals(employee.getId(), response.get(0).getId());
    }

    @Test
    void getBusinessEmployeesList_empty() {
        UUID businessId = UUID.randomUUID();

        when(employeeRepository.findByBusinessId(businessId))
                .thenReturn(List.of());

        List<EmployeeResponseDTO> response = employeeService.getBusinessEmployeesList(businessId);

        assertNotNull(response);
        assertTrue(response.isEmpty());
    }

    // ==================== getBusinessEmployees ====================

    @Test
    void getBusinessEmployees_success() {
        User owner = createBusinessOwner();
        Business business = createBusiness(owner);
        Employee employee = createEmployee(business);

        when(employeeRepository.findByBusinessId(business.getId()))
                .thenReturn(List.of(employee));

        Page<EmployeeResponseDTO> page = employeeService.getBusinessEmployees(
                business.getId(), PageRequest.of(0, 10));

        assertNotNull(page);
        assertEquals(1, page.getContent().size());
    }

    // ==================== getActiveEmployees ====================

    @Test
    void getActiveEmployees_success() {
        User owner = createBusinessOwner();
        Business business = createBusiness(owner);
        Employee employee = createEmployee(business);

        when(employeeRepository.findByBusinessIdAndActive(business.getId(), true))
                .thenReturn(List.of(employee));

        Page<EmployeeResponseDTO> page = employeeService.getActiveEmployees(
                business.getId(), PageRequest.of(0, 10));

        assertNotNull(page);
        assertEquals(1, page.getContent().size());
        assertTrue(page.getContent().get(0).getActive());
    }

    // ==================== updateEmployee ====================

    @Test
    void updateEmployee_success() {
        User owner = createBusinessOwner();
        Business business = createBusiness(owner);
        Employee employee = createEmployee(business);
        EmployeeRequestDTO request = createEmployeeRequest();
        request.setFirstName("Updated");
        request.setLastName("Name");

        when(businessRepository.findById(business.getId()))
                .thenReturn(Optional.of(business));

        when(employeeRepository.findById(employee.getId()))
                .thenReturn(Optional.of(employee));

        when(employeeRepository.save(any()))
                .thenAnswer(i -> i.getArgument(0));

        EmployeeResponseDTO response = employeeService.updateEmployee(
                business.getId(), employee.getId(), request, owner);

        assertNotNull(response);
        assertEquals("Updated", response.getFirstName());
        assertEquals("Name", response.getLastName());
        verify(employeeRepository).save(any());
    }

    @Test
    void updateEmployee_businessNotFound() {
        User owner = createBusinessOwner();
        UUID businessId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        EmployeeRequestDTO request = createEmployeeRequest();

        when(businessRepository.findById(businessId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> employeeService.updateEmployee(businessId, employeeId, request, owner));

        verify(employeeRepository, never()).save(any());
    }

    @Test
    void updateEmployee_notOwner_throwsBusinessException() {
        User owner = createBusinessOwner();
        User otherUser = createOtherUser();
        Business business = createBusiness(owner);
        UUID employeeId = UUID.randomUUID();
        EmployeeRequestDTO request = createEmployeeRequest();

        when(businessRepository.findById(business.getId()))
                .thenReturn(Optional.of(business));

        assertThrows(BusinessException.class,
                () -> employeeService.updateEmployee(business.getId(), employeeId, request, otherUser));

        verify(employeeRepository, never()).save(any());
    }

    // ==================== deleteEmployee ====================

    @Test
    void deleteEmployee_success() {
        User owner = createBusinessOwner();
        Business business = createBusiness(owner);
        Employee employee = createEmployee(business);

        when(businessRepository.findById(business.getId()))
                .thenReturn(Optional.of(business));

        when(employeeRepository.findById(employee.getId()))
                .thenReturn(Optional.of(employee));

        employeeService.deleteEmployee(business.getId(), employee.getId(), owner);

        verify(employeeRepository).delete(employee);
    }

    @Test
    void deleteEmployee_businessNotFound() {
        User owner = createBusinessOwner();
        UUID businessId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();

        when(businessRepository.findById(businessId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> employeeService.deleteEmployee(businessId, employeeId, owner));

        verify(employeeRepository, never()).delete(any());
    }

    @Test
    void deleteEmployee_notOwner_throwsBusinessException() {
        User owner = createBusinessOwner();
        User otherUser = createOtherUser();
        Business business = createBusiness(owner);
        UUID employeeId = UUID.randomUUID();

        when(businessRepository.findById(business.getId()))
                .thenReturn(Optional.of(business));

        assertThrows(BusinessException.class,
                () -> employeeService.deleteEmployee(business.getId(), employeeId, otherUser));

        verify(employeeRepository, never()).delete(any());
    }

    // ==================== Helpers ====================

    private User createBusinessOwner() {
        return User.builder()
                .id(UUID.randomUUID())
                .email(TEST_EMAIL)
                .role(User.UserRole.BUSINESS_ADMIN)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private User createOtherUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("other@gmail.com")
                .role(User.UserRole.BUSINESS_ADMIN)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private Business createBusiness(User owner) {
        return Business.builder()
                .id(UUID.randomUUID())
                .name("Test Business")
                .slug("test-business")
                .description("Test description")
                .address("123 Test Street")
                .city("Test City")
                .phone("+1234567890")
                .ratingOverall(0.0)
                .owner(owner)
                .employees(new ArrayList<>())
                .locations(new ArrayList<>())
                .workingHours(new ArrayList<>())
                .providedServices(new ArrayList<>())
                .features(new HashSet<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private Employee createEmployee(Business business) {
        return Employee.builder()
                .id(UUID.randomUUID())
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .phoneNumber("+1234567890")
                .photoUrl("https://example.com/photo.jpg")
                .active(true)
                .business(business)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private EmployeeRequestDTO createEmployeeRequest() {
        return EmployeeRequestDTO.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .phoneNumber("+1234567890")
                .photoUrl("https://example.com/photo.jpg")
                .active(true)
                .build();
    }
}