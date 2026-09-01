package com.platform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.controller.support.SecurityFilterChainTestConfig;
import com.platform.dto.business.BusinessRequestDTO;
import com.platform.dto.business.BusinessResponseDTO;
import com.platform.exception.ResourceNotFoundException;
import com.platform.service.BusinessService;
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
 * HTTP-layer coverage for {@link BusinessController} (BP-64): the public read
 * surface, role gating on the writes, validation, 404 mapping, and - the
 * load-bearing one - that the response still calls the image fields
 * {@code logoUrl} / {@code coverImageUrl} even though the columns behind them
 * were renamed to {@code *_key} in V6. Nothing else pins that contract.
 */
@WebMvcTest(controllers = BusinessController.class)
@Import(SecurityFilterChainTestConfig.class)
class BusinessControllerTest {

    private static final UUID BUSINESS_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @MockBean
    private BusinessService businessService;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtUtils jwtUtils;

    private BusinessResponseDTO businessDto() {
        return BusinessResponseDTO.builder()
                .id(BUSINESS_ID)
                .name("Olivia's Barbershop")
                .slug("olivias-barbershop")
                .city("Chisinau")
                .logoUrl("https://pub-test.r2.dev/business/" + BUSINESS_ID + "/logo/abc.png")
                .coverImageUrl("https://pub-test.r2.dev/business/" + BUSINESS_ID + "/cover/def.jpg")
                .build();
    }

    private BusinessRequestDTO validRequest() {
        return BusinessRequestDTO.builder()
                .name("Olivia's Barbershop")
                .address("1 Stefan cel Mare")
                .city("Chisinau")
                .phone("+37360000000")
                .build();
    }

    @Test
    void getById_isPublicAndKeepsTheImageFieldNames() throws Exception {
        when(businessService.getBusinessDTOById(BUSINESS_ID)).thenReturn(businessDto());

        mvc.perform(get("/api/business/{id}", BUSINESS_ID).with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Olivia's Barbershop"))
                // The frontend reads these exact keys; the entity columns are logo_key /
                // cover_image_key (V6), so only the DTO/mapper keep the contract.
                .andExpect(jsonPath("$.logoUrl").exists())
                .andExpect(jsonPath("$.coverImageUrl").exists())
                .andExpect(jsonPath("$.logoKey").doesNotExist())
                .andExpect(jsonPath("$.coverImageKey").doesNotExist());
    }

    @Test
    void getById_unknownBusiness_returns404() throws Exception {
        when(businessService.getBusinessDTOById(any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("Business not found"));

        mvc.perform(get("/api/business/{id}", UUID.randomUUID()).with(anonymous()))
                .andExpect(status().isNotFound());
    }

    @Test
    void createBusiness_asBusinessAdmin_returns201() throws Exception {
        // @CurrentUser resolves via the mocked UserService, so currentUser arrives null here -
        // any(User.class) would not match it, any() does.
        when(businessService.createBusiness(any(BusinessRequestDTO.class), any())).thenReturn(businessDto());

        mvc.perform(post("/api/business")
                        .with(user("owner@example.com").roles("BUSINESS_ADMIN"))
                        .contentType("application/json")
                        .content(json.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(BUSINESS_ID.toString()));
    }

    @Test
    void createBusiness_blankName_returns400() throws Exception {
        BusinessRequestDTO bad = validRequest();
        bad.setName("  ");

        mvc.perform(post("/api/business")
                        .with(user("owner@example.com").roles("BUSINESS_ADMIN"))
                        .contentType("application/json")
                        .content(json.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(businessService);
    }

    @Test
    void createBusiness_anonymous_returns401() throws Exception {
        mvc.perform(post("/api/business").with(anonymous())
                        .contentType("application/json")
                        .content(json.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createBusiness_asPlatformAdmin_returns403() throws Exception {
        // KNOWN-GAP (see BP-62): POST /api/business/** is BUSINESS_ADMIN-only, so a
        // PLATFORM_ADMIN is refused by the URL rule before the controller runs.
        mvc.perform(post("/api/business")
                        .with(user("admin@example.com").roles("PLATFORM_ADMIN"))
                        .contentType("application/json")
                        .content(json.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteBusiness_asOwner_returns204() throws Exception {
        mvc.perform(delete("/api/business/{id}", BUSINESS_ID)
                        .with(user("owner@example.com").roles("BUSINESS_ADMIN")))
                .andExpect(status().isNoContent());
    }
}
