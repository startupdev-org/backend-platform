package com.platform.service;

import com.platform.dto.availability.AvailabilityResponseDTO;
import com.platform.entity.Booking;
import com.platform.entity.Business;
import com.platform.entity.BusinessWorkingHours;
import com.platform.entity.Employee;
import com.platform.entity.ProvidedService;
import com.platform.exception.BadRequestException;
import com.platform.exception.ResourceNotFoundException;
import com.platform.repository.BookingRepository;
import com.platform.repository.BusinessRepository;
import com.platform.repository.BusinessWorkingHoursRepository;
import com.platform.repository.EmployeeRepository;
import com.platform.repository.ServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvailabilityServiceTest {

    @InjectMocks
    private AvailabilityService service;

    @Mock private BusinessRepository businessRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private ServiceRepository serviceRepository;
    @Mock private BusinessWorkingHoursRepository workingHoursRepository;
    @Mock private BookingRepository bookingRepository;

    private static final LocalDate FUTURE_DATE = LocalDate.of(2030, 6, 3); // a Monday

    private UUID businessId;
    private UUID employeeId;
    private UUID serviceId;
    private Business business;
    private Employee employee;
    private ProvidedService providedService;

    @BeforeEach
    void setUp() {
        businessId = UUID.randomUUID();
        employeeId = UUID.randomUUID();
        serviceId = UUID.randomUUID();

        business = Business.builder().id(businessId).build();
        employee = Employee.builder().id(employeeId).business(business).enabled(true).build();
        providedService = ProvidedService.builder()
                .id(serviceId).business(business).active(true).durationMinutes(60).build();
    }

    private void stubEntitiesFound() {
        when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(providedService));
    }

    private BusinessWorkingHours hours(LocalTime open, LocalTime close) {
        return new BusinessWorkingHours(business, DayOfWeek.MONDAY, open, close);
    }

    private Booking booking(LocalDateTime start, LocalDateTime end, Booking.BookingStatus status) {
        return Booking.builder().startTime(start).endTime(end).status(status).build();
    }

    @Test
    void noWorkingHoursRowForThatDay_returnsEmptyList() {
        stubEntitiesFound();
        when(workingHoursRepository.findByBusinessIdAndDayOfWeek(any(), any()))
                .thenReturn(List.of());

        AvailabilityResponseDTO result =
                service.getEmployeeAvailability(businessId, employeeId, serviceId, FUTURE_DATE);

        assertTrue(result.getAvailableSlots().isEmpty());
        assertEquals(60, result.getServiceDurationMinutes());
        assertEquals(FUTURE_DATE, result.getDate());
    }

    @Test
    void multipleIntervals_returnsSlotsFromEveryInterval() {
        stubEntitiesFound();
        when(workingHoursRepository.findByBusinessIdAndDayOfWeek(any(), any()))
                .thenReturn(List.of(
                        hours(LocalTime.of(9, 0), LocalTime.of(11, 0)),
                        hours(LocalTime.of(14, 0), LocalTime.of(16, 0))));
        when(bookingRepository.findByEmployeeAndDateRange(any(), any(), any()))
                .thenReturn(List.of());

        List<LocalDateTime> slots = service
                .getEmployeeAvailability(businessId, employeeId, serviceId, FUTURE_DATE)
                .getAvailableSlots();

        assertEquals(List.of(
                FUTURE_DATE.atTime(9, 0),
                FUTURE_DATE.atTime(9, 30),
                FUTURE_DATE.atTime(10, 0),
                FUTURE_DATE.atTime(14, 0),
                FUTURE_DATE.atTime(14, 30),
                FUTURE_DATE.atTime(15, 0)
        ), slots);
    }

    @Test
    void slotsOverlappingANonCancelledBooking_areExcluded() {
        stubEntitiesFound();
        when(workingHoursRepository.findByBusinessIdAndDayOfWeek(any(), any()))
                .thenReturn(List.of(hours(LocalTime.of(9, 0), LocalTime.of(12, 0))));
        when(bookingRepository.findByEmployeeAndDateRange(any(), any(), any()))
                .thenReturn(List.of(booking(
                        FUTURE_DATE.atTime(10, 0), FUTURE_DATE.atTime(11, 0),
                        Booking.BookingStatus.CONFIRMED)));

        List<LocalDateTime> slots = service
                .getEmployeeAvailability(businessId, employeeId, serviceId, FUTURE_DATE)
                .getAvailableSlots();

        assertEquals(List.of(
                FUTURE_DATE.atTime(9, 0),
                FUTURE_DATE.atTime(11, 0)
        ), slots);
    }

    @Test
    void slotFreedByACancelledBooking_isOffered() {
        stubEntitiesFound();
        when(workingHoursRepository.findByBusinessIdAndDayOfWeek(any(), any()))
                .thenReturn(List.of(hours(LocalTime.of(9, 0), LocalTime.of(12, 0))));
        when(bookingRepository.findByEmployeeAndDateRange(any(), any(), any()))
                .thenReturn(List.of(booking(
                        FUTURE_DATE.atTime(10, 0), FUTURE_DATE.atTime(11, 0),
                        Booking.BookingStatus.CANCELLED)));

        List<LocalDateTime> slots = service
                .getEmployeeAvailability(businessId, employeeId, serviceId, FUTURE_DATE)
                .getAvailableSlots();

        assertEquals(List.of(
                FUTURE_DATE.atTime(9, 0),
                FUTURE_DATE.atTime(9, 30),
                FUTURE_DATE.atTime(10, 0),
                FUTURE_DATE.atTime(10, 30),
                FUTURE_DATE.atTime(11, 0)
        ), slots);
    }

    @Test
    void pastSlotsForToday_areExcluded() {
        stubEntitiesFound();
        when(workingHoursRepository.findByBusinessIdAndDayOfWeek(any(), any()))
                .thenReturn(List.of(hours(LocalTime.of(0, 0), LocalTime.of(23, 30))));
        when(bookingRepository.findByEmployeeAndDateRange(any(), any(), any()))
                .thenReturn(List.of());

        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        List<LocalDateTime> slots = service
                .getEmployeeAvailability(businessId, employeeId, serviceId, today)
                .getAvailableSlots();

        assertFalse(slots.contains(today.atStartOfDay()));
        assertTrue(slots.stream().allMatch(s -> s.isAfter(now)));
    }

    @Test
    void businessNotFound_throwsResourceNotFound() {
        when(businessRepository.findById(businessId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                service.getEmployeeAvailability(businessId, employeeId, serviceId, FUTURE_DATE));
    }

    @Test
    void employeeBelongingToAnotherBusiness_throwsResourceNotFound() {
        employee.setBusiness(Business.builder().id(UUID.randomUUID()).build());
        when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));

        assertThrows(ResourceNotFoundException.class, () ->
                service.getEmployeeAvailability(businessId, employeeId, serviceId, FUTURE_DATE));
    }

    @Test
    void disabledEmployee_throwsResourceNotFound() {
        employee.setEnabled(false);
        when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));

        assertThrows(ResourceNotFoundException.class, () ->
                service.getEmployeeAvailability(businessId, employeeId, serviceId, FUTURE_DATE));
    }

    @Test
    void inactiveService_throwsResourceNotFound() {
        providedService.setActive(false);
        when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(providedService));

        assertThrows(ResourceNotFoundException.class, () ->
                service.getEmployeeAvailability(businessId, employeeId, serviceId, FUTURE_DATE));
    }

    @Test
    void missingServiceId_throwsBadRequest() {
        assertThrows(BadRequestException.class, () ->
                service.getEmployeeAvailability(businessId, employeeId, null, FUTURE_DATE));
    }

    @Test
    void missingDate_throwsBadRequest() {
        assertThrows(BadRequestException.class, () ->
                service.getEmployeeAvailability(businessId, employeeId, serviceId, null));
    }
}
