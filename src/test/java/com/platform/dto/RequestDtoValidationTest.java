package com.platform.dto;

import com.platform.dto.business.BusinessFeatureDTO;
import com.platform.dto.business.BusinessRequestDTO;
import com.platform.dto.business.CreateWorkingHoursRequest;
import com.platform.dto.employee.EmployeeRequestDTO;
import com.platform.dto.location.LocationRequestDTO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Constraint coverage for the DTOs that used to carry none. Runs the Bean
 * Validation engine directly - no Spring context - so it stays in step with the
 * rest of the suite.
 */
class RequestDtoValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    // ==================== LocationRequestDTO ====================

    @Test
    void location_emptyPayload_reportsEveryRequiredField() {
        Set<String> fields = violatedFields(new LocationRequestDTO());

        assertTrue(fields.containsAll(Set.of("name", "address", "city", "country")), fields.toString());
    }

    @Test
    void location_outOfRangeCoordinates_rejected() {
        LocationRequestDTO dto = validLocation();
        dto.setLatitude(91.0);
        dto.setLongitude(-181.0);

        assertEquals(Set.of("latitude", "longitude"), violatedFields(dto));
    }

    @Test
    void location_validPayload_passes() {
        assertTrue(validator.validate(validLocation()).isEmpty());
    }

    // ==================== CreateWorkingHoursRequest ====================

    // A null openTime used to reach validateTimeRange and NPE into a 500.
    @Test
    void workingHours_nullTimes_rejectedBeforeTheServiceSeesThem() {
        CreateWorkingHoursRequest request = CreateWorkingHoursRequest.builder()
                .dayOfWeek(DayOfWeek.MONDAY)
                .build();

        assertEquals(Set.of("openTime", "closeTime"), violatedFields(request));
    }

    @Test
    void workingHours_validPayload_passes() {
        CreateWorkingHoursRequest request = CreateWorkingHoursRequest.builder()
                .dayOfWeek(DayOfWeek.MONDAY)
                .openTime(LocalTime.of(9, 0))
                .closeTime(LocalTime.of(17, 0))
                .build();

        assertTrue(validator.validate(request).isEmpty());
    }

    // ==================== EmployeeLocationServicePriceRequestDTO ====================

    @Test
    void price_allNull_reportsEveryField() {
        var dto = new EmployeeLocationServicePriceRequestDTO(null, null, null, null);

        assertEquals(Set.of("employeeId", "serviceId", "locationId", "price"), violatedFields(dto));
    }

    @Test
    void price_negative_rejected() {
        var dto = new EmployeeLocationServicePriceRequestDTO(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("-1.00"));

        assertEquals(Set.of("price"), violatedFields(dto));
    }

    @Test
    void price_zeroOrPositive_passes() {
        var dto = new EmployeeLocationServicePriceRequestDTO(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("0.00"));

        assertTrue(validator.validate(dto).isEmpty());
    }

    // ==================== BusinessFeatureDTO ====================

    @Test
    void feature_blankName_rejected() {
        BusinessFeatureDTO dto = BusinessFeatureDTO.builder().name("  ").build();

        assertEquals(Set.of("name"), violatedFields(dto));
    }

    // ==================== smaller gaps in already-validated DTOs ====================

    @Test
    void business_malformedWebsite_rejected() {
        BusinessRequestDTO dto = BusinessRequestDTO.builder()
                .name("Barbershop")
                .address("Str. Stefan cel Mare 1")
                .city("Chisinau")
                .phone("+37360000000")
                .website("not a url")
                .build();

        assertEquals(Set.of("website"), violatedFields(dto));
    }

    @Test
    void employee_malformedEmail_rejected() {
        EmployeeRequestDTO dto = EmployeeRequestDTO.builder()
                .firstName("Ana")
                .lastName("Popescu")
                .email("ana@")
                .build();

        assertEquals(Set.of("email"), violatedFields(dto));
    }

    // ==================== helpers ====================

    private <T> Set<String> violatedFields(T target) {
        return validator.validate(target).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private LocationRequestDTO validLocation() {
        LocationRequestDTO dto = new LocationRequestDTO();
        dto.setName("Central");
        dto.setAddress("Str. Stefan cel Mare 1");
        dto.setCity("Chisinau");
        dto.setCountry("Moldova");
        dto.setLatitude(47.02);
        dto.setLongitude(28.83);
        return dto;
    }
}
