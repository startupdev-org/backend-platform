package com.platform.service;

import com.platform.dto.availability.AvailabilityResponseDTO;
import com.platform.entity.Booking;
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
import com.platform.utils.TimeSlotGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Read model behind {@code GET /api/business/{businessId}/employee/{employeeId}/availability}.
 *
 * <p>Slots come from the business's {@link BusinessWorkingHours} for the requested
 * weekday - never a hardcoded default - run through {@link TimeSlotGenerator}, then
 * with already-taken and past times removed. A weekday with no working-hours row
 * returns an empty list.
 *
 * <p>Public endpoint: no {@code currentUser}, no ownership check. It exposes only
 * what the public booking page already needs to render.
 */
@Service
@RequiredArgsConstructor
public class AvailabilityService {

    private final BusinessRepository businessRepository;
    private final EmployeeRepository employeeRepository;
    private final ServiceRepository serviceRepository;
    private final BusinessWorkingHoursRepository workingHoursRepository;
    private final BookingRepository bookingRepository;

    private static final String BUSINESS_NOT_FOUND = "Business not found";
    private static final String EMPLOYEE_NOT_FOUND = "Employee not found";
    private static final String SERVICE_NOT_FOUND = "Service not found";

    @Transactional(readOnly = true)
    public AvailabilityResponseDTO getEmployeeAvailability(UUID businessId,
                                                          UUID employeeId,
                                                          UUID serviceId,
                                                          LocalDate date) {
        if (serviceId == null) {
            throw new BadRequestException("serviceId is required");
        }
        if (date == null) {
            throw new BadRequestException("date is required");
        }

        businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException(BUSINESS_NOT_FOUND));

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException(EMPLOYEE_NOT_FOUND));
        if (!employee.getBusiness().getId().equals(businessId)
                || !Boolean.TRUE.equals(employee.getEnabled())) {
            throw new ResourceNotFoundException(EMPLOYEE_NOT_FOUND);
        }

        ProvidedService service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException(SERVICE_NOT_FOUND));
        if (!service.getBusiness().getId().equals(businessId)
                || !Boolean.TRUE.equals(service.getActive())) {
            throw new ResourceNotFoundException(SERVICE_NOT_FOUND);
        }

        int durationMinutes = service.getDurationMinutes();

        List<LocalDateTime> slots = computeSlots(businessId, employeeId, date, durationMinutes);

        return AvailabilityResponseDTO.builder()
                .employeeId(employeeId)
                .serviceId(serviceId)
                .date(date)
                .serviceDurationMinutes(durationMinutes)
                .availableSlots(slots)
                .build();
    }

    private List<LocalDateTime> computeSlots(UUID businessId,
                                             UUID employeeId,
                                             LocalDate date,
                                             int durationMinutes) {

        List<BusinessWorkingHours> intervals =
                workingHoursRepository.findByBusinessIdAndDayOfWeek(businessId, date.getDayOfWeek());

        if (intervals.isEmpty()) {
            return List.of();
        }

        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        List<Booking> takenBookings = bookingRepository
                .findByEmployeeAndDateRange(employeeId, dayStart, dayStart.plusDays(1))
                .stream()
                .filter(b -> b.getStatus() != Booking.BookingStatus.CANCELLED)
                .toList();

        return intervals.stream()
                .flatMap(wh -> TimeSlotGenerator.generateAvailableSlots(
                        dayStart, durationMinutes, wh.getOpenTime(), wh.getCloseTime()).stream())
                .distinct()
                .filter(start -> start.isAfter(now))
                .filter(start -> isFree(start, start.plusMinutes(durationMinutes), takenBookings))
                .sorted()
                .toList();
    }

    private boolean isFree(LocalDateTime start, LocalDateTime end, List<Booking> takenBookings) {
        return takenBookings.stream()
                .noneMatch(b -> start.isBefore(b.getEndTime()) && end.isAfter(b.getStartTime()));
    }
}
