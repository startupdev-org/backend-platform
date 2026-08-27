package com.platform.service;

import com.platform.dto.business.BusinessFeatureDTO;
import com.platform.dto.employee.EmployeeResponseDTO;
import com.platform.dto.location.LocationResponseDTO;
import com.platform.dto.service.ServiceResponseDTO;
import com.platform.entity.*;
import com.platform.enums.ServiceDeliveryType;
import com.platform.repository.BusinessRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import com.platform.dto.business.BusinessRequestDTO;
import com.platform.dto.business.BusinessResponseDTO;
import com.platform.exception.BusinessException;
import com.platform.exception.ResourceNotFoundException;
import com.platform.storage.ImageUrlResolver;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BusinessServiceTest {

    @InjectMocks
    private BusinessService businessService;

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private ProvidedServicesService providedServicesService;

    @Mock
    private UserService userService;

    @Mock
    private EmployeeService employeeService;

    @Mock
    private LocationService locationService;

    @Mock
    private FeatureService featureService;

    @Mock
    private ImageUrlResolver imageUrls;

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

    // ----------------------------
    // Helpers
    // ----------------------------

    private User createBusinessAdmin() {
        return User.builder()
                .id(UUID.randomUUID())
                .email(TEST_EMAIL)
                .role(User.UserRole.BUSINESS_ADMIN)
                .build();
    }

    private User createPlatformAdmin() {
        return User.builder()
                .id(UUID.randomUUID())
                .email(TEST_EMAIL)
                .role(User.UserRole.PLATFORM_ADMIN)
                .build();
    }

    private BusinessRequestDTO createRequest() {
        return BusinessRequestDTO.builder()
                .name("Test Business")
                .description("desc")
                .address("address")
                .city("Chisinau")
                .phone("123")
                .website("site")
                .build();
    }

    private Business createBusiness(User owner) {

        UUID businessId = UUID.randomUUID();

        Business business = Business.builder()
                .id(businessId)
                .name("Test Business")
                .slug("test-business")
                .description("Test description")
                .address("123 Test Street")
                .city("Test City")
                .phone("+1234567890")
                .ratingOverall(0.0)
                .owner(owner)
                .employees(createEmployeeList())
                .locations(new ArrayList<>())
                .workingHours(new ArrayList<>())
                .providedServices(new ArrayList<>())
                .features(createFeatureList())
                .serviceDeliveryType(ServiceDeliveryType.ON_SITE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        List<BusinessWorkingHours> hoursList = new ArrayList<>();
        hoursList.add(new BusinessWorkingHours(business, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0)));
        business.setWorkingHours(hoursList);

        return business;
    }

    private List<Location> createLocationList() {
        Location locationOne = Location.builder()
                .name("Location One")
                .build();

        Location locationSecond = Location.builder()
                .name("Location Second")
                .build();

        Location locationThird = Location.builder()
                .name("Location Third")
                .build();

        return List.of(locationOne, locationSecond, locationThird);
    }

    private List<LocationResponseDTO> createLocationResponseDTOList() {
        LocationResponseDTO locationOne = LocationResponseDTO.builder()
                .name("Location One")
                .build();

        LocationResponseDTO locationSecond = LocationResponseDTO.builder()
                .name("Location Second")
                .build();

        LocationResponseDTO locationThird = LocationResponseDTO.builder()
                .name("Location Third")
                .build();

        return List.of(locationOne, locationSecond, locationThird);
    }

    private Set<BusinessFeature> createFeatureList() {
        BusinessFeature featureOne = BusinessFeature.builder()
                .name("Feature One")
                .build();

        BusinessFeature featureSecond = BusinessFeature.builder()
                .name("Feature Second")
                .build();

        BusinessFeature featureThird = BusinessFeature.builder()
                .name("Feature Third")
                .build();

        return Set.of(featureOne, featureSecond, featureThird);
    }

    private Set<BusinessFeatureDTO> createFeatureDTOList() {

        BusinessFeatureDTO featureOne = BusinessFeatureDTO.builder()
                .name("Feature One")
                .build();

        BusinessFeatureDTO featureSecond = BusinessFeatureDTO.builder()
                .name("Feature Second")
                .build();

        BusinessFeatureDTO featureThird = BusinessFeatureDTO.builder()
                .name("Feature Third")
                .build();

        return Set.of(featureOne, featureSecond, featureThird);
    }

    private List<Employee> createEmployeeList() {
        Employee employeeOne = Employee.builder()
                .firstName("Employee")
                .lastName("One")
                .build();

        Employee employeeSecond = Employee.builder()
                .firstName("Employee")
                .lastName("Second")
                .build();

        Employee employeeThird = Employee.builder()
                .firstName("Employee")
                .lastName("Third")
                .build();

        return List.of(employeeOne, employeeSecond, employeeThird);
    }

    private List<EmployeeResponseDTO> createEmployeeDTOList() {
        EmployeeResponseDTO employeeOne = EmployeeResponseDTO.builder()
                .firstName("EmployeeResponseDTO")
                .lastName("One")
                .build();

        EmployeeResponseDTO employeeSecond = EmployeeResponseDTO.builder()
                .firstName("EmployeeResponseDTO")
                .lastName("Second")
                .build();

        EmployeeResponseDTO employeeThird = EmployeeResponseDTO.builder()
                .firstName("Employee")
                .lastName("Third")
                .build();

        return List.of(employeeOne, employeeSecond, employeeThird);
    }

    private List<ServiceResponseDTO> createProvidedServicesDTOList() {
        ServiceResponseDTO serviceOne = ServiceResponseDTO.builder()
                .name("Service One")
                .build();

        return List.of(serviceOne);
    }

    // ----------------------------
    // createBusiness
    // ----------------------------

    @Test
    void createBusiness_success() {

        User owner = createBusinessAdmin();
        BusinessRequestDTO dto = createRequest();

        when(userService.getUserByUsername(owner.getEmail()))
                .thenReturn(owner);

        when(businessRepository.save(any()))
                .thenAnswer(i -> i.getArgument(0));

        when(userService.getUserById(owner.getId()))
                .thenReturn(owner);

        when(employeeService.getBusinessEmployeesList(any()))
                .thenReturn(new ArrayList<>());

        when(locationService.getLocationsForBusiness(any()))
                .thenReturn(createLocationResponseDTOList());

        BusinessResponseDTO response =
                businessService.createBusiness(dto);

        assertNotNull(response);
        assertEquals(dto.getName(), response.getName());

        verify(businessRepository).save(any());
    }

    @Test
    void createBusiness_error() {

        User platformAdmin = createPlatformAdmin();
        BusinessRequestDTO dto = createRequest();


        when(userService.getUserByUsername(platformAdmin.getEmail()))
                .thenReturn(platformAdmin);

        assertThrows(BusinessException.class, () ->
                businessService.createBusiness(dto));

        verify(businessRepository, never()).save(any());
    }

    // ----------------------------
    // getBusinessById
    // ----------------------------

    @Test
    void getBusinessDTOById_success() {

        User owner = createBusinessAdmin();
        Business business = createBusiness(owner);

        when(businessRepository.findById(business.getId()))
                .thenReturn(Optional.of(business));

        when(userService.getUserById(owner.getId()))
                .thenReturn(owner);

        when(providedServicesService.getBusinessServices(any()))
                .thenReturn(createProvidedServicesDTOList());

        when(employeeService.getBusinessEmployeesList(any()))
                .thenReturn(createEmployeeDTOList());

        when(featureService.getAllFeatures(any()))
                .thenReturn(createFeatureDTOList());

        when(locationService.getLocationsForBusiness(any()))
                .thenReturn(createLocationResponseDTOList());

        BusinessResponseDTO dto =
                businessService.getBusinessDTOById(business.getId());

        assertEquals(business.getId(), dto.getId());
    }



    @Test
    void getBusinessDTOById_notFound() {

        UUID id = UUID.randomUUID();

        when(businessRepository.findById(id))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> businessService.getBusinessDTOById(id)
        );
    }

    // ----------------------------
    // getBusinessBySlug
    // ----------------------------

    @Test
    void getBusinessBySlug_success() {

        User owner = createBusinessAdmin();
        Business business = createBusiness(owner);

        when(businessRepository.findBySlug(business.getSlug()))
                .thenReturn(Optional.of(business));

        when(providedServicesService.getBusinessServices(business.getId()))
                .thenReturn(List.of());

        when(userService.getUserById(owner.getId()))
                .thenReturn(owner);

        when(employeeService.getBusinessEmployeesList(business.getId()))
                .thenReturn(List.of());

        when(locationService.getLocationsForBusiness(any()))
                .thenReturn(createLocationResponseDTOList());

        BusinessResponseDTO dto =
                businessService.getBusinessBySlug(business.getSlug());

        assertEquals(business.getSlug(), dto.getSlug());
    }

    // ----------------------------
    // listBusinesses
    // ----------------------------

    @Test
    void listBusinesses_withCity() {

        User owner = createBusinessAdmin();
        Business business = createBusiness(owner);

        when(businessRepository.findByCity(eq("Chisinau"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(business)));

        when(providedServicesService.getBusinessServices(business.getId()))
                .thenReturn(List.of());

        when(userService.getUserById(owner.getId()))
                .thenReturn(owner);

        when(employeeService.getBusinessEmployeesList(business.getId()))
                .thenReturn(List.of());

        when(locationService.getLocationsForBusiness(any()))
                .thenReturn(createLocationResponseDTOList());

        Page<BusinessResponseDTO> page =
                businessService.listBusinesses(
                        "Chisinau",
                        null,
                        BusinessCategoryType.BARBERSHOP.name(),
                        PageRequest.of(0, 10)
                );

        assertEquals(1, page.getContent().size());
    }

    /**
     * The regression this ticket exists for: listBusinesses used to load the whole
     * table and wrap it in a PageImpl, so every page returned identical content and
     * totalPages was always 1. The repository stub here slices on the Pageable it is
     * handed, exactly as Spring Data does, so a service that ignores the Pageable fails.
     */
    @Test
    void listBusinesses_pagesThroughTwentyFiveRows() {

        User owner = createBusinessAdmin();
        List<Business> allBusinesses = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            Business business = createBusiness(owner);
            business.setName("Business " + i);
            allBusinesses.add(business);
        }

        when(businessRepository.findAll(any(Pageable.class))).thenAnswer(invocation -> {
            Pageable pageable = invocation.getArgument(0);
            int from = (int) pageable.getOffset();
            int to = Math.min(from + pageable.getPageSize(), allBusinesses.size());
            return new PageImpl<>(allBusinesses.subList(from, to), pageable, allBusinesses.size());
        });

        when(providedServicesService.getBusinessServices(any(UUID.class))).thenReturn(List.of());
        when(userService.getUserById(any())).thenReturn(owner);
        when(employeeService.getBusinessEmployeesList(any(UUID.class))).thenReturn(List.of());
        when(locationService.getLocationsForBusiness(any())).thenReturn(createLocationResponseDTOList());

        Page<BusinessResponseDTO> firstPage =
                businessService.listBusinesses(null, null, null, PageRequest.of(0, 10));
        Page<BusinessResponseDTO> lastPage =
                businessService.listBusinesses(null, null, null, PageRequest.of(2, 10));

        assertEquals(10, firstPage.getContent().size());
        assertEquals(5, lastPage.getContent().size());
        assertEquals(25, firstPage.getTotalElements());
        assertEquals(3, firstPage.getTotalPages());
        assertEquals(3, lastPage.getTotalPages());

        assertNotEquals(
                firstPage.getContent().stream().map(BusinessResponseDTO::getId).toList(),
                lastPage.getContent().stream().map(BusinessResponseDTO::getId).toList());
    }

    @Test
    void listBusinessesByQuery_passesPageableToRepository() {

        User owner = createBusinessAdmin();
        Business business = createBusiness(owner);
        PageRequest pageable = PageRequest.of(1, 1);

        when(businessRepository.findByNameContainingIgnoreCase("Test", pageable))
                .thenReturn(new PageImpl<>(List.of(business), pageable, 7));

        when(providedServicesService.getBusinessServices(any(UUID.class))).thenReturn(List.of());
        when(userService.getUserById(any())).thenReturn(owner);
        when(employeeService.getBusinessEmployeesList(any(UUID.class))).thenReturn(List.of());
        when(locationService.getLocationsForBusiness(any())).thenReturn(createLocationResponseDTOList());

        Page<BusinessResponseDTO> page = businessService.listBusinessesByQuery("Test", pageable);

        assertEquals(1, page.getContent().size());
        assertEquals(7, page.getTotalElements());
        assertEquals(7, page.getTotalPages());
        verify(businessRepository, never()).findByNameContainingIgnoreCase(anyString());
    }

    // ----------------------------
    // updateBusiness
    // ----------------------------

    @Test
    void updateBusiness_success_owner() {

        User owner = createBusinessAdmin();
        Business business = createBusiness(owner);

        when(userService.getUserByUsername(owner.getEmail()))
                .thenReturn(owner);

        when(businessRepository.findById(business.getId()))
                .thenReturn(Optional.of(business));

        when(businessRepository.save(any()))
                .thenAnswer(i -> i.getArgument(0));

        when(providedServicesService.getBusinessServices(business.getId()))
                .thenReturn(List.of());

        when(userService.getUserById(owner.getId()))
                .thenReturn(owner);

        when(employeeService.getBusinessEmployeesList(business.getId()))
                .thenReturn(List.of());

        when(locationService.getLocationsForBusiness(any()))
                .thenReturn(createLocationResponseDTOList());

        BusinessResponseDTO dto =
                businessService.updateBusiness(
                        business.getId(),
                        createRequest()
                );

        assertEquals("Test Business", dto.getName());
    }

    @Test
    void updateBusiness_shouldThrow_unauthorized() {

        User owner = createBusinessAdmin();
        Business business = createBusiness(owner);

        UUID businessId = business.getId();
        BusinessRequestDTO request = createRequest();

        User otherUser = createBusinessAdmin();

        when(userService.getUserByUsername(owner.getEmail()))
                .thenReturn(otherUser);

        when(businessRepository.findById(business.getId()))
                .thenReturn(Optional.of(business));


        assertThrows(
                BusinessException.class,
                () -> businessService.updateBusiness(
                        businessId,
                        request
                )
        );
    }

    // ----------------------------
    // deleteBusiness
    // ----------------------------

    @Test
    void deleteBusiness_success_platformAdmin() {

        User owner = createBusinessAdmin();
        Business business = createBusiness(owner);

        User platformAdmin = createPlatformAdmin();

        when(businessRepository.findById(business.getId()))
                .thenReturn(Optional.of(business));

        businessService.deleteBusiness(business.getId(), platformAdmin);

        verify(businessRepository).delete(business);
    }

    // ----------------------------
    // getUserBusinesses
    // ----------------------------

    @Test
    void getUserBusinesses_success() {

        UUID userId = UUID.randomUUID();

        User owner = createBusinessAdmin();
        owner.setId(userId);

        Business business = createBusiness(owner);

        when(businessRepository.findByOwnerId(userId))
                .thenReturn(List.of(business));

        when(providedServicesService.getBusinessServices(business.getId()))
                .thenReturn(List.of());

        when(userService.getUserById(owner.getId()))
                .thenReturn(owner);

        when(employeeService.getBusinessEmployeesList(business.getId()))
                .thenReturn(List.of());

        when(locationService.getLocationsForBusiness(any()))
                .thenReturn(createLocationResponseDTOList());

        List<BusinessResponseDTO> result =
                businessService.getUserBusinesses(userId);

        assertEquals(1, result.size());
    }
}
