package com.platform.service;

import com.platform.dto.booking.BookingRequestDTO;
import com.platform.dto.booking.BookingResponseDTO;
import com.platform.entity.Booking;
import com.platform.entity.Business;
import com.platform.entity.Employee;
import com.platform.entity.EmployeeLocationServicePrice;
import com.platform.entity.Location;
import com.platform.entity.ProvidedService;
import com.platform.entity.User;
import com.platform.exception.BusinessException;
import com.platform.exception.ResourceNotFoundException;
import com.platform.repository.BookingRepository;
import com.platform.repository.BusinessRepository;
import com.platform.repository.EmployeeLocationServicePriceRepository;
import com.platform.repository.EmployeeRepository;
import com.platform.repository.LocationRepository;
import com.platform.repository.ServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BusinessRepository businessRepository;
    private final EmployeeRepository employeeRepository;
    private final ServiceRepository serviceRepository;
    private final LocationRepository locationRepository;
    private final EmployeeLocationServicePriceRepository priceRepository;

    private static final String BOOKING_NOT_FOUND = "Booking not found";
    private static final String EMPLOYEE_NOT_FOUND = "Employee not found";
    private static final String BUSINESS_NOT_FOUND = "Business not found";

    @Transactional
    public BookingResponseDTO createBooking(BookingRequestDTO dto) {
        Employee employee = employeeRepository.findById(dto.getEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        ProvidedService providedService = serviceRepository.findById(dto.getServiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Service not found"));

        Location location = resolveLocation(dto.getLocationId(), employee);

        EmployeeLocationServicePrice priceEntry = priceRepository
                .findByEmployeeIdAndServiceIdAndLocationId(
                        dto.getEmployeeId(),
                        dto.getServiceId(),
                        location.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee does not offer this service at the requested location"));

        LocalDateTime endTime = dto.getStartTime().plusMinutes(providedService.getDurationMinutes());

        List<Booking> conflictingBookings = bookingRepository.findByEmployeeAndDateRange(
                dto.getEmployeeId(),
                dto.getStartTime(),
                endTime);

        if (!conflictingBookings.isEmpty()) {
            throw new BusinessException("Employee not available at this time");
        }

        Booking booking = Booking.builder()
                .customerName(dto.getCustomerName())
                .customerPhone(dto.getCustomerPhone())
                .customerEmail(dto.getCustomerEmail())
                .startTime(dto.getStartTime())
                .endTime(endTime)
                .priceEntry(priceEntry)
                .status(Booking.BookingStatus.CONFIRMED)
                .build();

        booking = bookingRepository.save(booking);
        return toDTO(booking);
    }

    private Location resolveLocation(UUID locationId, Employee employee) {
        if (locationId != null) {
            return locationRepository.findById(locationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Location not found"));
        }
        return locationRepository.findByBusinessIdAndIsDefaultLocationTrue(employee.getBusiness().getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No default location found and no location specified in request"));
    }

    public BookingResponseDTO getBooking(UUID id, User currentUser) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(BOOKING_NOT_FOUND));
        assertCanAccessBooking(booking, currentUser);
        return toDTO(booking);
    }

    /**
     * Bookings visible to {@code currentUser}. A BUSINESS_ADMIN only ever sees
     * bookings of the businesses they own; PLATFORM_ADMIN sees all. The no-filter
     * path used to fall through to an unscoped findAll() that returned every
     * customer's PII platform-wide. See BP-29.
     */
    public List<BookingResponseDTO> listBookings(UUID employeeId, Booking.BookingStatus status, User currentUser) {
        boolean platformAdmin = isPlatformAdmin(currentUser);

        List<Booking> bookings;
        if (employeeId != null) {
            Employee employee = employeeRepository.findById(employeeId)
                    .orElseThrow(() -> new ResourceNotFoundException(EMPLOYEE_NOT_FOUND));
            if (!platformAdmin && employee.getBusiness().isNotOwner(currentUser)) {
                // Do not confirm the employee exists to a caller from another tenant.
                throw new ResourceNotFoundException(EMPLOYEE_NOT_FOUND);
            }
            bookings = (status != null)
                    ? bookingRepository.findByEmployeeIdAndStatusForListing(employeeId, status)
                    : bookingRepository.findByEmployeeIdForListing(employeeId);
        } else if (platformAdmin) {
            bookings = (status != null)
                    ? bookingRepository.findByStatusForListing(status)
                    : bookingRepository.findAllForListing();
        } else {
            List<UUID> ownedBusinessIds = businessRepository.findByOwnerId(currentUser.getId())
                    .stream().map(Business::getId).toList();
            if (ownedBusinessIds.isEmpty()) {
                return List.of();
            }
            bookings = (status != null)
                    ? bookingRepository.findByBusinessIdInAndStatusForListing(ownedBusinessIds, status)
                    : bookingRepository.findByBusinessIdInForListing(ownedBusinessIds);
        }
        return bookings.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<BookingResponseDTO> getEmployeeBookings(
            UUID employeeId, LocalDateTime startDate, LocalDateTime endDate, User currentUser) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException(EMPLOYEE_NOT_FOUND));
        if (!isPlatformAdmin(currentUser) && employee.getBusiness().isNotOwner(currentUser)) {
            throw new ResourceNotFoundException(EMPLOYEE_NOT_FOUND);
        }
        return bookingRepository.findByEmployeeAndDateRange(employeeId, startDate, endDate)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public BookingResponseDTO updateBookingStatus(UUID id, Booking.BookingStatus newStatus, User currentUser) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(BOOKING_NOT_FOUND));
        assertCanAccessBooking(booking, currentUser);

        booking.setStatus(newStatus);
        booking = bookingRepository.save(booking);
        return toDTO(booking);
    }

    @Transactional
    public void cancelBooking(UUID id, User currentUser) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(BOOKING_NOT_FOUND));
        assertCanAccessBooking(booking, currentUser);

        if (booking.getStatus().equals(Booking.BookingStatus.COMPLETED)) {
            throw new BusinessException("Cannot cancel a completed booking");
        }

        booking.setStatus(Booking.BookingStatus.CANCELLED);
        bookingRepository.save(booking);
    }

    public List<BookingResponseDTO> getBusinessBookings(
            UUID businessId, Booking.BookingStatus status, User currentUser) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException(BUSINESS_NOT_FOUND));
        if (!isPlatformAdmin(currentUser) && business.isNotOwner(currentUser)) {
            throw new ResourceNotFoundException(BUSINESS_NOT_FOUND);
        }
        return bookingRepository.findByBusinessAndStatus(businessId, status)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // ── Authorization ─────────────────────────────────────────────────────────

    private boolean isPlatformAdmin(User user) {
        return user.getRole() == User.UserRole.PLATFORM_ADMIN;
    }

    /**
     * A booking belongs to the business of the employee it was booked with. A
     * caller who is neither that business's owner nor a PLATFORM_ADMIN is told the
     * booking does not exist rather than that it is forbidden - a 403 would confirm
     * a probed id belongs to a real booking of another tenant. See BP-29.
     */
    private void assertCanAccessBooking(Booking booking, User currentUser) {
        if (isPlatformAdmin(currentUser)) {
            return;
        }
        Business business = booking.getEmployee().getBusiness();
        if (business == null || business.isNotOwner(currentUser)) {
            throw new ResourceNotFoundException(BOOKING_NOT_FOUND);
        }
    }

    private BookingResponseDTO toDTO(Booking booking) {
        return BookingResponseDTO.builder()
                .id(booking.getId())
                .customerName(booking.getCustomerName())
                .customerPhone(booking.getCustomerPhone())
                .customerEmail(booking.getCustomerEmail())
                .employeeId(booking.getEmployee().getId())
                .serviceId(booking.getProvidedService().getId())
                .startTime(booking.getStartTime())
                .endTime(booking.getEndTime())
                .status(booking.getStatus().name())
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .build();
    }
}
