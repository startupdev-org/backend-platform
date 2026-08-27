package com.platform.service;

import com.platform.dto.booking.BookingRequestDTO;
import com.platform.dto.booking.BookingResponseDTO;
import com.platform.entity.Booking;
import com.platform.entity.Employee;
import com.platform.entity.EmployeeLocationServicePrice;
import com.platform.entity.Location;
import com.platform.entity.ProvidedService;
import com.platform.exception.BusinessException;
import com.platform.exception.ResourceNotFoundException;
import com.platform.repository.BookingRepository;
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
    private final EmployeeRepository employeeRepository;
    private final ServiceRepository serviceRepository;
    private final LocationRepository locationRepository;
    private final EmployeeLocationServicePriceRepository priceRepository;

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

    public BookingResponseDTO getBooking(UUID id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        return toDTO(booking);
    }

    public List<BookingResponseDTO> listBookings(UUID employeeId, Booking.BookingStatus status) {
        // Fetch the whole graph toDTO touches (priceEntry -> employee/service, review)
        // in one query per branch, and push the status filter into the database rather
        // than hydrating every row and filtering in memory. See BP-53.
        List<Booking> bookings;
        if (employeeId != null && status != null) {
            bookings = bookingRepository.findByEmployeeIdAndStatusForListing(employeeId, status);
        } else if (employeeId != null) {
            bookings = bookingRepository.findByEmployeeIdForListing(employeeId);
        } else if (status != null) {
            bookings = bookingRepository.findByStatusForListing(status);
        } else {
            bookings = bookingRepository.findAllForListing();
        }
        return bookings.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<BookingResponseDTO> getEmployeeBookings(UUID employeeId, LocalDateTime startDate, LocalDateTime endDate) {
        return bookingRepository.findByEmployeeAndDateRange(employeeId, startDate, endDate)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public BookingResponseDTO updateBookingStatus(UUID id, Booking.BookingStatus newStatus) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        booking.setStatus(newStatus);
        booking = bookingRepository.save(booking);
        return toDTO(booking);
    }

    @Transactional
    public void cancelBooking(UUID id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        if (booking.getStatus().equals(Booking.BookingStatus.COMPLETED)) {
            throw new BusinessException("Cannot cancel a completed booking");
        }

        booking.setStatus(Booking.BookingStatus.CANCELLED);
        bookingRepository.save(booking);
    }

    public List<BookingResponseDTO> getBusinessBookings(UUID businessId, Booking.BookingStatus status) {
        return bookingRepository.findByBusinessAndStatus(businessId, status)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
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
