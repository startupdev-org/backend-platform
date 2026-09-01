package com.platform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.controller.support.SecurityFilterChainTestConfig;
import com.platform.dto.employee.EmployeeRequestDTO;
import com.platform.dto.employee.EmployeeResponseDTO;
import com.platform.exception.ResourceNotFoundException;
import com.platform.service.AvailabilityService;
import com.platform.service.EmployeeLocationServicePriceService;
import com.platform.service.EmployeeService;
import com.platform.service.UserService;
import com.platform.utils.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real HTTP-layer coverage for {@link EmployeeController} (BP-64), replacing the
 * six-line empty stub that had quietly implied controller coverage since the repo
 * began. Covers the public read, role gating on writes, validation, 404 mapping,
 * and pins {@code photoUrl} as the response field name (the column is photo_key).
 */
@WebMvcTest(controllers = EmployeeController.class)
@Import(SecurityFilterChainTestConfig.class)
class EmployeeControllerTest {

    private static final UUID BUSINESS_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID EMPLOYEE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @MockBean
    private EmployeeService employeeService;

    @MockBean
    private AvailabilityService availabilityService;

    @MockBean
    private EmployeeLocationServicePriceService priceService;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtUtils jwtUtils;

    private EmployeeResponseDTO employeeDto() {
        return EmployeeResponseDTO.builder()
                .id(EMPLOYEE_ID)
                .firstName("Alex")
                .lastName("Stylist")
                .businessId(BUSINESS_ID)
                .enabled(true)
                .photoUrl("https://pub-test.r2.dev/business/" + BUSINESS_ID + "/employee/" + EMPLOYEE_ID + "/p.png")
                .build();
    }

    private EmployeeRequestDTO validRequest() {
        return EmployeeRequestDTO.builder()
                .firstName("Alex")
                .lastName("Stylist")
                .email("alex@example.com")
                .build();
    }

    @Test
    void getEmployee_isPublicAndUsesPhotoUrlAsTheFieldName() throws Exception {
        when(employeeService.getEmployee(EMPLOYEE_ID)).thenReturn(employeeDto());

        mvc.perform(get("/api/business/{b}/employee/{e}", BUSINESS_ID, EMPLOYEE_ID).with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Alex"))
                .andExpect(jsonPath("$.photoUrl").exists())
                .andExpect(jsonPath("$.photoKey").doesNotExist());
    }

    @Test
    void getEmployee_unknownEmployee_returns404() throws Exception {
        when(employeeService.getEmployee(any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("Employee not found"));

        mvc.perform(get("/api/business/{b}/employee/{e}", BUSINESS_ID, UUID.randomUUID()).with(anonymous()))
                .andExpect(status().isNotFound());
    }

    @Test
    void createEmployee_asBusinessAdmin_returns201() throws Exception {
        when(employeeService.createEmployee(eq(BUSINESS_ID), any(EmployeeRequestDTO.class)))
                .thenReturn(employeeDto());

        mvc.perform(post("/api/business/{b}/employee", BUSINESS_ID)
                        .with(user("owner@example.com").roles("BUSINESS_ADMIN"))
                        .contentType("application/json")
                        .content(json.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(EMPLOYEE_ID.toString()));
    }

    @Test
    void createEmployee_firstNameTooShort_returns400() throws Exception {
        EmployeeRequestDTO bad = validRequest();
        bad.setFirstName("A");

        mvc.perform(post("/api/business/{b}/employee", BUSINESS_ID)
                        .with(user("owner@example.com").roles("BUSINESS_ADMIN"))
                        .contentType("application/json")
                        .content(json.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(employeeService);
    }

    @Test
    void createEmployee_anonymous_returns401() throws Exception {
        mvc.perform(post("/api/business/{b}/employee", BUSINESS_ID).with(anonymous())
                        .contentType("application/json")
                        .content(json.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteEmployee_asPlatformAdmin_returns403_soft() throws Exception {
        // The soft-delete DELETE is BUSINESS_ADMIN-only in SecurityConfig; permanent
        // delete is the PLATFORM_ADMIN path.
        mvc.perform(delete("/api/business/{b}/employee/{e}", BUSINESS_ID, EMPLOYEE_ID)
                        .with(user("admin@example.com").roles("PLATFORM_ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteEmployee_asBusinessAdmin_returns204() throws Exception {
        mvc.perform(delete("/api/business/{b}/employee/{e}", BUSINESS_ID, EMPLOYEE_ID)
                        .with(user("owner@example.com").roles("BUSINESS_ADMIN")))
                .andExpect(status().isNoContent());
    }
}
