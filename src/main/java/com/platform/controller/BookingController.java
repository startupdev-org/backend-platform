package com.platform.controller;

import com.platform.dto.booking.BookingRequestDTO;
import com.platform.dto.booking.BookingResponseDTO;
import com.platform.entity.Booking;
import com.platform.entity.User;
import com.platform.security.CurrentUser;
import com.platform.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Creating a booking is public - customers book without an account (BP-46) - and is
 * throttled per IP. Every other route is management data, role-gated in SecurityConfig
 * and ownership-scoped to the calling owner's businesses in BookingService (BP-29).
 *
 * <p>A caller from another tenant is told a booking does not exist rather than that it is
 * forbidden, so the reads below document 404 where a 403 might be expected.
 *
 * <p>bearerAuth is declared per method, not on the class, so the empty
 * {@code @SecurityRequirements} on the public create is not overridden.
 */
@Tag(name = "Booking", description = "Booking creation and management endpoints")
@RestController
@RequestMapping("/api/booking")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @Operation(summary = "Create a booking",
            description = "Books a service with an employee at a given time. Public - a customer "
                    + "books without an account. Rate-limited per IP.")
    @ApiResponse(responseCode = "201", description = "Booking created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @ApiResponse(responseCode = "403", description = "The employee is already booked over the requested slot")
    @ApiResponse(responseCode = "404", description = "Employee, service or location not found, "
            + "or the employee does not offer this service at that location")
    @ApiResponse(responseCode = "429", description = "Too many requests from this IP")
    @SecurityRequirements
    @PostMapping
    public ResponseEntity<BookingResponseDTO> createBooking(@Valid @RequestBody BookingRequestDTO request) {
        BookingResponseDTO booking = bookingService.createBooking(request);
        return new ResponseEntity<>(booking, HttpStatus.CREATED);
    }

    @Operation(summary = "Get booking by ID",
            description = "Returns a single booking. Only a booking belonging to one of the caller's businesses is visible.")
    @ApiResponse(responseCode = "200", description = "Booking found")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "403", description = "Caller is not a business or platform administrator")
    @ApiResponse(responseCode = "404", description = "Booking not found, or it belongs to another business")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{id}")
    public ResponseEntity<BookingResponseDTO> getBooking(
            @Parameter(description = "Booking UUID", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID id,
            @CurrentUser User currentUser) {
        BookingResponseDTO booking = bookingService.getBooking(id, currentUser);
        return ResponseEntity.ok(booking);
    }

    @Operation(summary = "List bookings",
            description = "Returns the bookings of every business the caller owns, optionally "
                    + "narrowed to one employee or one status. Never returns another owner's bookings.")
    @ApiResponse(responseCode = "200", description = "Bookings retrieved successfully")
    @ApiResponse(responseCode = "400", description = "Unknown status value")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "403", description = "Caller is not a business or platform administrator")
    @ApiResponse(responseCode = "404", description = "Employee not found, or it belongs to another business")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    public ResponseEntity<List<BookingResponseDTO>> listBookings(
            @Parameter(description = "Only bookings for this employee")
            @RequestParam(required = false) UUID employeeId,
            @Parameter(description = "Only bookings in this status", example = "CONFIRMED")
            @RequestParam(required = false) Booking.BookingStatus status,
            @CurrentUser User currentUser) {
        List<BookingResponseDTO> bookings =
                bookingService.listBookings(employeeId, status, currentUser);
        return ResponseEntity.ok(bookings);
    }

    @Operation(summary = "List an employee's bookings in a date range",
            description = "Returns one employee's bookings between two timestamps - the calendar view")
    @ApiResponse(responseCode = "200", description = "Bookings retrieved successfully")
    @ApiResponse(responseCode = "400", description = "Malformed timestamp")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "403", description = "Caller is not a business or platform administrator")
    @ApiResponse(responseCode = "404", description = "Employee not found, or it belongs to another business")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/employee/{employeeId}/range")
    public ResponseEntity<List<BookingResponseDTO>> getEmployeeBookingsByRange(
            @Parameter(description = "Employee UUID")
            @PathVariable UUID employeeId,
            @Parameter(description = "Range start, as ISO-8601 date-time", example = "2026-09-01T00:00:00")
            @RequestParam LocalDateTime startDate,
            @Parameter(description = "Range end, as ISO-8601 date-time", example = "2026-09-30T23:59:59")
            @RequestParam LocalDateTime endDate,
            @CurrentUser User currentUser) {
        List<BookingResponseDTO> bookings = bookingService.getEmployeeBookings(
                employeeId, startDate, endDate, currentUser);
        return ResponseEntity.ok(bookings);
    }

    @Operation(summary = "Update booking status",
            description = "Moves a booking to a new status, for example confirming or completing it")
    @ApiResponse(responseCode = "200", description = "Status updated successfully")
    @ApiResponse(responseCode = "400", description = "Unknown status value")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "403", description = "Caller is not a business or platform administrator")
    @ApiResponse(responseCode = "404", description = "Booking not found, or it belongs to another business")
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/{id}/status")
    public ResponseEntity<BookingResponseDTO> updateBookingStatus(
            @Parameter(description = "Booking UUID")
            @PathVariable UUID id,
            @Parameter(description = "New status", example = "CONFIRMED")
            @RequestParam Booking.BookingStatus status,
            @CurrentUser User currentUser) {
        BookingResponseDTO booking =
                bookingService.updateBookingStatus(id, status, currentUser);
        return ResponseEntity.ok(booking);
    }

    @Operation(summary = "Cancel a booking",
            description = "Cancels a booking, freeing its slot for other customers. A completed booking cannot be cancelled.")
    @ApiResponse(responseCode = "204", description = "Booking cancelled successfully")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "403", description = "Caller is not a business or platform administrator, "
            + "or the booking is already completed")
    @ApiResponse(responseCode = "404", description = "Booking not found, or it belongs to another business")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelBooking(
            @Parameter(description = "Booking UUID")
            @PathVariable UUID id,
            @CurrentUser User currentUser) {
        bookingService.cancelBooking(id, currentUser);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "List a business's bookings by status",
            description = "Returns every booking of one business in the given status. Caller must own the business.")
    @ApiResponse(responseCode = "200", description = "Bookings retrieved successfully")
    @ApiResponse(responseCode = "400", description = "Unknown status value")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "403", description = "Caller is not a business or platform administrator")
    @ApiResponse(responseCode = "404", description = "Business not found, or it belongs to another owner")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/business/{businessId}")
    public ResponseEntity<List<BookingResponseDTO>> getBusinessBookings(
            @Parameter(description = "Business UUID")
            @PathVariable UUID businessId,
            @Parameter(description = "Status to filter by", example = "PENDING")
            @RequestParam Booking.BookingStatus status,
            @CurrentUser User currentUser) {
        List<BookingResponseDTO> bookings =
                bookingService.getBusinessBookings(businessId, status, currentUser);
        return ResponseEntity.ok(bookings);
    }
}
