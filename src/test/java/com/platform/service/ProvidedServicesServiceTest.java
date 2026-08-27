package com.platform.service;

import com.platform.dto.service.ServiceRequestDTO;
import com.platform.dto.service.ServiceResponseDTO;
import com.platform.entity.Booking;
import com.platform.entity.Business;
import com.platform.entity.ProvidedService;
import com.platform.entity.User;
import com.platform.exception.BusinessException;
import com.platform.exception.ResourceNotFoundException;
import com.platform.exception.ServiceNotFoundException;
import com.platform.repository.BookingRepository;
import com.platform.repository.BusinessRepository;
import com.platform.repository.ServiceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProvidedServicesServiceTest {

    @InjectMocks
    private ProvidedServicesService providedServicesService;

    @Mock private ServiceRepository serviceRepository;
    @Mock private UserService userService;
    @Mock private BusinessRepository businessRepository;
    @Mock private BookingRepository bookingRepository;

    private static final String EMAIL = "owner@example.com";

    private User owner;
    private Business ownedBusiness;

    @BeforeEach
    void setUp() {
        owner = user(User.UserRole.BUSINESS_ADMIN);
        ownedBusiness = business(owner);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(EMAIL, null,
                        List.of(new SimpleGrantedAuthority("ROLE_BUSINESS_ADMIN"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── createService ─────────────────────────────────────────────────────────

    @Test
    void createService_ownerCreatesAnActiveServiceOnTheirBusiness() {
        stubCurrentUser(owner);
        when(businessRepository.findById(ownedBusiness.getId())).thenReturn(Optional.of(ownedBusiness));
        when(serviceRepository.save(any(ProvidedService.class))).thenAnswer(i -> {
            ProvidedService saved = i.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        ServiceResponseDTO response = providedServicesService.createService(ownedBusiness.getId(), request());

        ArgumentCaptor<ProvidedService> captor = ArgumentCaptor.forClass(ProvidedService.class);
        verify(serviceRepository).save(captor.capture());
        assertEquals(ownedBusiness, captor.getValue().getBusiness());
        // New services start active; the DTO's own active flag is ignored on create.
        assertTrue(captor.getValue().getActive());
        assertEquals("Haircut", response.getName());
        assertEquals(ownedBusiness.getId(), response.getBusinessId());
    }

    @Test
    void createService_rejectsANonOwner() {
        User stranger = user(User.UserRole.BUSINESS_ADMIN);
        stubCurrentUser(stranger);
        when(businessRepository.findById(ownedBusiness.getId())).thenReturn(Optional.of(ownedBusiness));

        assertThrows(BusinessException.class,
                () -> providedServicesService.createService(ownedBusiness.getId(), request()));

        verify(serviceRepository, never()).save(any());
    }

    @Test
    void createService_allowsAPlatformAdminOnAnyBusiness() {
        stubCurrentUser(user(User.UserRole.PLATFORM_ADMIN));
        when(businessRepository.findById(ownedBusiness.getId())).thenReturn(Optional.of(ownedBusiness));
        when(serviceRepository.save(any(ProvidedService.class))).thenAnswer(i -> i.getArgument(0));

        providedServicesService.createService(ownedBusiness.getId(), request());

        verify(serviceRepository).save(any(ProvidedService.class));
    }

    @Test
    void createService_rejectsAnUnknownBusiness() {
        UUID missing = UUID.randomUUID();
        when(businessRepository.findById(missing)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> providedServicesService.createService(missing, request()));

        verifyNoInteractions(serviceRepository);
    }

    // ── updateService ─────────────────────────────────────────────────────────

    @Test
    void updateService_ownerUpdatesTheirOwnService() {
        ProvidedService existing = service(ownedBusiness, "Old", true);
        stubCurrentUser(owner);
        when(businessRepository.findById(ownedBusiness.getId())).thenReturn(Optional.of(ownedBusiness));
        when(serviceRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(serviceRepository.save(existing)).thenReturn(existing);

        ServiceResponseDTO response =
                providedServicesService.updateService(ownedBusiness.getId(), existing.getId(), request());

        assertEquals("Haircut", response.getName());
        assertEquals(new BigDecimal("250.00"), response.getPrice());
        assertEquals(45, response.getDurationMinutes());
    }

    @Test
    void updateService_rejectsANonOwner() {
        User stranger = user(User.UserRole.BUSINESS_ADMIN);
        stubCurrentUser(stranger);
        when(businessRepository.findById(ownedBusiness.getId())).thenReturn(Optional.of(ownedBusiness));

        assertThrows(BusinessException.class, () -> providedServicesService
                .updateService(ownedBusiness.getId(), UUID.randomUUID(), request()));

        verify(serviceRepository, never()).save(any());
    }

    @Test
    void updateService_rejectsAnUnknownService() {
        UUID missing = UUID.randomUUID();
        stubCurrentUser(owner);
        when(businessRepository.findById(ownedBusiness.getId())).thenReturn(Optional.of(ownedBusiness));
        when(serviceRepository.findById(missing)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> providedServicesService.updateService(ownedBusiness.getId(), missing, request()));
    }

    /** A null {@code active} means "leave it as it is", not "deactivate". */
    @Test
    void updateService_leavesTheActiveFlagAloneWhenTheRequestOmitsIt() {
        ProvidedService existing = service(ownedBusiness, "Old", false);
        stubCurrentUser(owner);
        when(businessRepository.findById(ownedBusiness.getId())).thenReturn(Optional.of(ownedBusiness));
        when(serviceRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(serviceRepository.save(existing)).thenReturn(existing);

        ServiceRequestDTO withoutActive = request();
        withoutActive.setActive(null);

        assertEquals(false, providedServicesService
                .updateService(ownedBusiness.getId(), existing.getId(), withoutActive).getActive());
    }

    // ── BP-34: the cross-business hole, pinned as it currently behaves ────────

    /**
     * Ownership is checked against the {@code businessId} in the path, but the service is
     * then loaded by id alone with no check that it belongs to that business. So the owner
     * of business A can edit any service of business B by passing their own businessId
     * with B's serviceId - the ownership check passes on a business that has nothing to do
     * with the row being written.
     *
     * <p>This test pins the broken behaviour deliberately, so BP-34 has a target that
     * fails the moment the check is added. When that fix lands, this test should be
     * rewritten to expect a rejection, not deleted.
     */
    @Test
    void updateService_currentlyAllowsEditingAnotherBusinessesService_BP34() {
        Business otherBusiness = business(user(User.UserRole.BUSINESS_ADMIN));
        ProvidedService theirService = service(otherBusiness, "Their service", true);

        stubCurrentUser(owner);
        when(businessRepository.findById(ownedBusiness.getId())).thenReturn(Optional.of(ownedBusiness));
        when(serviceRepository.findById(theirService.getId())).thenReturn(Optional.of(theirService));
        when(serviceRepository.save(theirService)).thenReturn(theirService);

        providedServicesService.updateService(ownedBusiness.getId(), theirService.getId(), request());

        // Documented defect: the write went through, onto a row of a business the caller
        // does not own, and the service still belongs to that other business.
        assertEquals("Haircut", theirService.getName());
        assertEquals(otherBusiness, theirService.getBusiness());
    }

    /** {@code deleteService} has the same shape of hole, and it also deletes the bookings. */
    @Test
    void deleteService_currentlyAllowsDeletingAnotherBusinessesService_BP34() {
        Business otherBusiness = business(user(User.UserRole.BUSINESS_ADMIN));
        ProvidedService theirService = service(otherBusiness, "Their service", true);

        when(businessRepository.findById(ownedBusiness.getId())).thenReturn(Optional.of(ownedBusiness));
        when(serviceRepository.findById(theirService.getId())).thenReturn(Optional.of(theirService));
        when(bookingRepository.findByProvidedServiceId(theirService.getId())).thenReturn(List.of());

        providedServicesService.deleteService(ownedBusiness.getId(), theirService.getId(), owner);

        verify(serviceRepository).delete(theirService);
    }

    // ── deleteService ─────────────────────────────────────────────────────────

    /**
     * Bookings reference the service through the price-entry FK, so they have to go first
     * or the delete fails. Note this destroys booking history rather than soft-deleting
     * the service.
     */
    @Test
    void deleteService_removesTheServicesBookingsBeforeTheServiceItself() {
        ProvidedService existing = service(ownedBusiness, "Cut", true);
        List<Booking> bookings = List.of(
                Booking.builder().id(UUID.randomUUID()).build(),
                Booking.builder().id(UUID.randomUUID()).build());

        when(businessRepository.findById(ownedBusiness.getId())).thenReturn(Optional.of(ownedBusiness));
        when(serviceRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(bookingRepository.findByProvidedServiceId(existing.getId())).thenReturn(bookings);

        providedServicesService.deleteService(ownedBusiness.getId(), existing.getId(), owner);

        InOrder order = inOrder(bookingRepository, serviceRepository);
        order.verify(bookingRepository).deleteAll(bookings);
        order.verify(serviceRepository).delete(existing);
    }

    @Test
    void deleteService_rejectsANonOwner() {
        User stranger = user(User.UserRole.BUSINESS_ADMIN);
        when(businessRepository.findById(ownedBusiness.getId())).thenReturn(Optional.of(ownedBusiness));

        assertThrows(BusinessException.class, () -> providedServicesService
                .deleteService(ownedBusiness.getId(), UUID.randomUUID(), stranger));

        verify(serviceRepository, never()).delete(any());
        verifyNoInteractions(bookingRepository);
    }

    @Test
    void deleteService_rejectsAnUnknownService() {
        UUID missing = UUID.randomUUID();
        when(businessRepository.findById(ownedBusiness.getId())).thenReturn(Optional.of(ownedBusiness));
        when(serviceRepository.findById(missing)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> providedServicesService.deleteService(ownedBusiness.getId(), missing, owner));
    }

    // ── Reads ─────────────────────────────────────────────────────────────────

    @Test
    void getService_returnsTheService() {
        ProvidedService existing = service(ownedBusiness, "Cut", true);
        when(serviceRepository.findById(existing.getId())).thenReturn(Optional.of(existing));

        assertEquals("Cut", providedServicesService.getService(existing.getId()).getName());
    }

    /** Note the read path raises {@code ServiceNotFoundException} where the write paths raise
     *  {@code ResourceNotFoundException}; both map to 404, so the difference is invisible
     *  over HTTP. */
    @Test
    void getService_rejectsAnUnknownService() {
        UUID missing = UUID.randomUUID();
        when(serviceRepository.findById(missing)).thenReturn(Optional.empty());

        assertThrows(ServiceNotFoundException.class, () -> providedServicesService.getService(missing));
    }

    @Test
    void getActiveServices_asksTheRepositoryForActiveOnly() {
        when(serviceRepository.findByBusinessIdAndActive(ownedBusiness.getId(), true))
                .thenReturn(List.of(service(ownedBusiness, "Cut", true)));

        assertEquals(1, providedServicesService.getActiveServices(ownedBusiness.getId()).size());

        verify(serviceRepository).findByBusinessIdAndActive(ownedBusiness.getId(), true);
    }

    // ── BP-53: batch lookup ───────────────────────────────────────────────────

    @Test
    void getServicesByBusinessIds_groupsTheResultByBusiness() {
        Business other = business(user(User.UserRole.BUSINESS_ADMIN));
        List<UUID> ids = List.of(ownedBusiness.getId(), other.getId());
        when(serviceRepository.findByBusinessIdIn(ids)).thenReturn(List.of(
                service(ownedBusiness, "Cut", true),
                service(ownedBusiness, "Shave", true),
                service(other, "Massage", true)));

        Map<UUID, List<ServiceResponseDTO>> grouped =
                providedServicesService.getServicesByBusinessIds(ids);

        assertEquals(2, grouped.get(ownedBusiness.getId()).size());
        assertEquals(1, grouped.get(other.getId()).size());
    }

    /** An empty id collection must not reach the repository - {@code IN ()} is not valid SQL. */
    @Test
    void getServicesByBusinessIds_shortCircuitsOnAnEmptyCollection() {
        assertEquals(Map.of(), providedServicesService.getServicesByBusinessIds(List.of()));

        verifyNoInteractions(serviceRepository);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void stubCurrentUser(User user) {
        when(userService.getUserByUsername(anyString())).thenReturn(user);
    }

    private User user(User.UserRole role) {
        return User.builder().id(UUID.randomUUID()).email(EMAIL).role(role).build();
    }

    private Business business(User owner) {
        return Business.builder().id(UUID.randomUUID()).owner(owner).name("Salon").build();
    }

    private ProvidedService service(Business business, String name, boolean active) {
        return ProvidedService.builder()
                .id(UUID.randomUUID())
                .business(business)
                .name(name)
                .price(new BigDecimal("100.00"))
                .durationMinutes(30)
                .active(active)
                .build();
    }

    private ServiceRequestDTO request() {
        ServiceRequestDTO dto = new ServiceRequestDTO();
        dto.setName("Haircut");
        dto.setDescription("A haircut");
        dto.setPrice(new BigDecimal("250.00"));
        dto.setDurationMinutes(45);
        dto.setActive(true);
        return dto;
    }
}
