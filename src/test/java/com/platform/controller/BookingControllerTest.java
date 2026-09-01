package com.platform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.controller.support.SecurityFilterChainTestConfig;
import com.platform.dto.booking.BookingRequestDTO;
import com.platform.dto.booking.BookingResponseDTO;
import com.platform.entity.Booking;
import com.platform.exception.ResourceNotFoundException;
import com.platform.service.BookingService;
import com.platform.service.UserService;
import com.platform.utils.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-layer coverage for {@link BookingController} (BP-64): the anonymous create
 * path, its validation, and that every management route is closed to anonymous
 * callers and role-gated to BUSINESS_ADMIN / PLATFORM_ADMIN.
 */
@WebMvcTest(controllers = BookingController.class, properties = "rate-limit.enabled=false")
@Import(SecurityFilterChainTestConfig.class)
class BookingControllerTest {

    private static final UUID BOOKING_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @MockBean
    private BookingService bookingService;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtUtils jwtUtils;

    private BookingResponseDTO bookingDto() {
        return BookingResponseDTO.builder()
                .id(BOOKING_ID)
                .customerName("Jane Customer")
                .customerEmail("jane@example.com")
                .status("CONFIRMED")
                .startTime(LocalDateTime.of(2026, 9, 10, 10, 0))
                .build();
    }

    private BookingRequestDTO validRequest() {
        BookingRequestDTO r = new BookingRequestDTO();
        r.setCustomerName("Jane Customer");
        r.setCustomerPhone("+37369000000");
        r.setCustomerEmail("jane@example.com");
        r.setEmployeeId(UUID.randomUUID());
        r.setServiceId(UUID.randomUUID());
        r.setStartTime(LocalDateTime.of(2026, 9, 10, 10, 0));
        return r;
    }

    @Test
    void createBooking_isPublic_returns201() throws Exception {
        when(bookingService.createBooking(any(BookingRequestDTO.class))).thenReturn(bookingDto());

        mvc.perform(post("/api/booking").with(anonymous())
                        .contentType("application/json")
                        .content(json.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(BOOKING_ID.toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void createBooking_missingEmployeeId_returns400() throws Exception {
        BookingRequestDTO bad = validRequest();
        bad.setEmployeeId(null);

        mvc.perform(post("/api/booking").with(anonymous())
                        .contentType("application/json")
                        .content(json.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(bookingService);
    }

    @Test
    void getBooking_anonymous_returns401() throws Exception {
        mvc.perform(get("/api/booking/{id}", BOOKING_ID).with(anonymous()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(bookingService);
    }

    @Test
    void getBooking_asBusinessAdmin_returns200() throws Exception {
        when(bookingService.getBooking(any(UUID.class), any())).thenReturn(bookingDto());

        mvc.perform(get("/api/booking/{id}", BOOKING_ID)
                        .with(user("owner@example.com").roles("BUSINESS_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("Jane Customer"));
    }

    @Test
    void getBooking_anotherTenantsBooking_maps404() throws Exception {
        when(bookingService.getBooking(any(UUID.class), any()))
                .thenThrow(new ResourceNotFoundException("Booking not found"));

        mvc.perform(get("/api/booking/{id}", BOOKING_ID)
                        .with(user("owner@example.com").roles("BUSINESS_ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateBookingStatus_asBusinessAdmin_returns200() throws Exception {
        when(bookingService.updateBookingStatus(any(UUID.class), any(Booking.BookingStatus.class), any()))
                .thenReturn(bookingDto());

        mvc.perform(patch("/api/booking/{id}/status", BOOKING_ID)
                        .param("status", "COMPLETED")
                        .with(user("owner@example.com").roles("BUSINESS_ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void updateBookingStatus_anonymous_returns401() throws Exception {
        mvc.perform(patch("/api/booking/{id}/status", BOOKING_ID)
                        .param("status", "COMPLETED")
                        .with(anonymous()))
                .andExpect(status().isUnauthorized());
    }
}
