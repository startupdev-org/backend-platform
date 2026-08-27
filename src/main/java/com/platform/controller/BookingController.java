package com.platform.controller;

import com.platform.dto.booking.BookingRequestDTO;
import com.platform.dto.booking.BookingResponseDTO;
import com.platform.entity.Booking;
import com.platform.entity.User;
import com.platform.service.BookingService;
import com.platform.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/booking")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<BookingResponseDTO> createBooking(@Valid @RequestBody BookingRequestDTO request) {
        BookingResponseDTO booking = bookingService.createBooking(request);
        return new ResponseEntity<>(booking, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookingResponseDTO> getBooking(@PathVariable UUID id, Authentication authentication) {
        BookingResponseDTO booking = bookingService.getBooking(id, currentUser(authentication));
        return ResponseEntity.ok(booking);
    }

    @GetMapping
    public ResponseEntity<List<BookingResponseDTO>> listBookings(
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) Booking.BookingStatus status,
            Authentication authentication) {
        List<BookingResponseDTO> bookings =
                bookingService.listBookings(employeeId, status, currentUser(authentication));
        return ResponseEntity.ok(bookings);
    }

    @GetMapping("/employee/{employeeId}/range")
    public ResponseEntity<List<BookingResponseDTO>> getEmployeeBookingsByRange(
            @PathVariable UUID employeeId,
            @RequestParam LocalDateTime startDate,
            @RequestParam LocalDateTime endDate,
            Authentication authentication) {
        List<BookingResponseDTO> bookings = bookingService.getEmployeeBookings(
                employeeId, startDate, endDate, currentUser(authentication));
        return ResponseEntity.ok(bookings);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<BookingResponseDTO> updateBookingStatus(
            @PathVariable UUID id,
            @RequestParam Booking.BookingStatus status,
            Authentication authentication) {
        BookingResponseDTO booking =
                bookingService.updateBookingStatus(id, status, currentUser(authentication));
        return ResponseEntity.ok(booking);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelBooking(@PathVariable UUID id, Authentication authentication) {
        bookingService.cancelBooking(id, currentUser(authentication));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/business/{businessId}")
    public ResponseEntity<List<BookingResponseDTO>> getBusinessBookings(
            @PathVariable UUID businessId,
            @RequestParam Booking.BookingStatus status,
            Authentication authentication) {
        List<BookingResponseDTO> bookings =
                bookingService.getBusinessBookings(businessId, status, currentUser(authentication));
        return ResponseEntity.ok(bookings);
    }

    private User currentUser(Authentication authentication) {
        return userService.getUserByUsername(authentication.getName());
    }
}
