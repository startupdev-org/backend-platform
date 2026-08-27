package com.platform.service;

import com.platform.entity.Booking;
import com.platform.repository.BookingRepository;
import com.platform.repository.EmployeeLocationServicePriceRepository;
import com.platform.repository.EmployeeRepository;
import com.platform.repository.LocationRepository;
import com.platform.repository.ServiceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @InjectMocks
    private BookingService bookingService;

    @Mock private BookingRepository bookingRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private ServiceRepository serviceRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private EmployeeLocationServicePriceRepository priceRepository;

    // BP-53: listBookings must load the DTO graph in one query per branch and never
    // fall back to the unscoped, non-fetching findAll() / findByEmployeeId() /
    // findByStatus() that fanned out to ~2N+1 queries.

    @Test
    void listBookings_noFilters_usesFetchGraphAndNotFindAll() {
        when(bookingRepository.findAllForListing()).thenReturn(List.of());

        bookingService.listBookings(null, null);

        verify(bookingRepository).findAllForListing();
        verify(bookingRepository, never()).findAll();
    }

    @Test
    void listBookings_employeeAndStatus_pushesFilterIntoQuery() {
        UUID employeeId = UUID.randomUUID();
        when(bookingRepository.findByEmployeeIdAndStatusForListing(employeeId, Booking.BookingStatus.CONFIRMED))
                .thenReturn(List.of());

        bookingService.listBookings(employeeId, Booking.BookingStatus.CONFIRMED);

        verify(bookingRepository).findByEmployeeIdAndStatusForListing(employeeId, Booking.BookingStatus.CONFIRMED);
        verify(bookingRepository, never()).findByEmployeeId(employeeId);
        verify(bookingRepository, never()).findByStatus(Booking.BookingStatus.CONFIRMED);
    }

    @Test
    void listBookings_statusOnly_usesFetchGraph() {
        when(bookingRepository.findByStatusForListing(Booking.BookingStatus.CANCELLED))
                .thenReturn(List.of());

        bookingService.listBookings(null, Booking.BookingStatus.CANCELLED);

        verify(bookingRepository).findByStatusForListing(Booking.BookingStatus.CANCELLED);
        verify(bookingRepository, never()).findByStatus(Booking.BookingStatus.CANCELLED);
    }

    @Test
    void listBookings_employeeOnly_usesFetchGraph() {
        UUID employeeId = UUID.randomUUID();
        when(bookingRepository.findByEmployeeIdForListing(employeeId)).thenReturn(List.of());

        bookingService.listBookings(employeeId, null);

        verify(bookingRepository).findByEmployeeIdForListing(employeeId);
        verify(bookingRepository, never()).findByEmployeeId(employeeId);
    }
}
