package com.platform.service;

import com.platform.entity.Booking;
import com.platform.entity.Business;
import com.platform.entity.Employee;
import com.platform.entity.EmployeeLocationServicePrice;
import com.platform.entity.ProvidedService;
import com.platform.entity.User;
import com.platform.exception.ResourceNotFoundException;
import com.platform.repository.BookingRepository;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @InjectMocks
    private BookingService bookingService;

    @Mock private BookingRepository bookingRepository;
    @Mock private BusinessRepository businessRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private ServiceRepository serviceRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private EmployeeLocationServicePriceRepository priceRepository;

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User user(User.UserRole role) {
        return User.builder().id(UUID.randomUUID()).email("u@x.io").role(role).build();
    }

    private Business businessOwnedBy(User owner) {
        return Business.builder().id(UUID.randomUUID()).owner(owner).build();
    }

    private Booking bookingIn(Business business) {
        Employee employee = Employee.builder().id(UUID.randomUUID()).business(business).build();
        ProvidedService service = ProvidedService.builder().id(UUID.randomUUID()).build();
        EmployeeLocationServicePrice price = EmployeeLocationServicePrice.builder()
                .id(UUID.randomUUID()).employee(employee).service(service).build();
        return Booking.builder()
                .id(UUID.randomUUID())
                .priceEntry(price)
                .status(Booking.BookingStatus.CONFIRMED)
                .startTime(LocalDateTime.now())
                .endTime(LocalDateTime.now().plusMinutes(30))
                .build();
    }

    // ── BP-53: listBookings uses the fetch graph, never unscoped findAll() ─────

    @Test
    void listBookings_platformAdmin_noFilters_usesFetchGraphNotFindAll() {
        when(bookingRepository.findAllForListing()).thenReturn(List.of());

        bookingService.listBookings(null, null, user(User.UserRole.PLATFORM_ADMIN));

        verify(bookingRepository).findAllForListing();
        verify(bookingRepository, never()).findAll();
    }

    @Test
    void listBookings_platformAdmin_statusOnly_pushesFilterIntoQuery() {
        when(bookingRepository.findByStatusForListing(Booking.BookingStatus.CANCELLED)).thenReturn(List.of());

        bookingService.listBookings(null, Booking.BookingStatus.CANCELLED, user(User.UserRole.PLATFORM_ADMIN));

        verify(bookingRepository).findByStatusForListing(Booking.BookingStatus.CANCELLED);
        verify(bookingRepository, never()).findByStatus(any());
    }

    // ── BP-29: listBookings is scoped to the caller's businesses ──────────────

    @Test
    void listBookings_businessAdmin_noFilters_scopesToOwnedBusinessesNeverFindAll() {
        User owner = user(User.UserRole.BUSINESS_ADMIN);
        Business owned = businessOwnedBy(owner);
        when(businessRepository.findByOwnerId(owner.getId())).thenReturn(List.of(owned));
        when(bookingRepository.findByBusinessIdInForListing(anyCollection())).thenReturn(List.of());

        bookingService.listBookings(null, null, owner);

        verify(bookingRepository).findByBusinessIdInForListing(List.of(owned.getId()));
        verify(bookingRepository, never()).findAllForListing();
        verify(bookingRepository, never()).findAll();
    }

    @Test
    void listBookings_businessAdmin_ownsNothing_returnsEmptyWithoutQuery() {
        User owner = user(User.UserRole.BUSINESS_ADMIN);
        when(businessRepository.findByOwnerId(owner.getId())).thenReturn(List.of());

        List<?> result = bookingService.listBookings(null, null, owner);

        assertTrue(result.isEmpty());
        verify(bookingRepository, never()).findAllForListing();
        verify(bookingRepository, never()).findByBusinessIdInForListing(anyCollection());
    }

    @Test
    void listBookings_byEmployee_ofAnotherTenant_isNotFound() {
        User caller = user(User.UserRole.BUSINESS_ADMIN);
        Business otherBusiness = businessOwnedBy(user(User.UserRole.BUSINESS_ADMIN));
        Employee foreignEmployee = Employee.builder().id(UUID.randomUUID()).business(otherBusiness).build();
        when(employeeRepository.findById(foreignEmployee.getId())).thenReturn(Optional.of(foreignEmployee));

        assertThrows(ResourceNotFoundException.class,
                () -> bookingService.listBookings(foreignEmployee.getId(), null, caller));

        verify(bookingRepository, never()).findByEmployeeIdForListing(any());
    }

    // ── BP-29: single-booking access is ownership scoped, 404 across tenants ──

    @Test
    void getBooking_ownedByCaller_isReturned() {
        User owner = user(User.UserRole.BUSINESS_ADMIN);
        Booking booking = bookingIn(businessOwnedBy(owner));
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        assertEquals(booking.getId(), bookingService.getBooking(booking.getId(), owner).getId());
    }

    @Test
    void getBooking_ofAnotherTenant_isNotFound() {
        Booking booking = bookingIn(businessOwnedBy(user(User.UserRole.BUSINESS_ADMIN)));
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        assertThrows(ResourceNotFoundException.class,
                () -> bookingService.getBooking(booking.getId(), user(User.UserRole.BUSINESS_ADMIN)));
    }

    @Test
    void getBooking_asPlatformAdmin_isAllowed() {
        Booking booking = bookingIn(businessOwnedBy(user(User.UserRole.BUSINESS_ADMIN)));
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        assertEquals(booking.getId(),
                bookingService.getBooking(booking.getId(), user(User.UserRole.PLATFORM_ADMIN)).getId());
    }

    @Test
    void updateBookingStatus_ofAnotherTenant_isNotFoundAndNotPersisted() {
        Booking booking = bookingIn(businessOwnedBy(user(User.UserRole.BUSINESS_ADMIN)));
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        assertThrows(ResourceNotFoundException.class, () -> bookingService.updateBookingStatus(
                booking.getId(), Booking.BookingStatus.COMPLETED, user(User.UserRole.BUSINESS_ADMIN)));

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void cancelBooking_ofAnotherTenant_isNotFoundAndNotPersisted() {
        Booking booking = bookingIn(businessOwnedBy(user(User.UserRole.BUSINESS_ADMIN)));
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        assertThrows(ResourceNotFoundException.class,
                () -> bookingService.cancelBooking(booking.getId(), user(User.UserRole.BUSINESS_ADMIN)));

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void getBusinessBookings_ofAnotherTenant_isNotFound() {
        Business business = businessOwnedBy(user(User.UserRole.BUSINESS_ADMIN));
        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));

        assertThrows(ResourceNotFoundException.class, () -> bookingService.getBusinessBookings(
                business.getId(), Booking.BookingStatus.CONFIRMED, user(User.UserRole.BUSINESS_ADMIN)));

        verify(bookingRepository, never()).findByBusinessAndStatus(any(), any());
    }
}
