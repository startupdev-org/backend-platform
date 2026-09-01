package com.platform.integration;

import com.platform.entity.Booking;
import com.platform.entity.Business;
import com.platform.entity.Employee;
import com.platform.entity.EmployeeLocationServicePrice;
import com.platform.entity.Location;
import com.platform.entity.ProvidedService;
import com.platform.entity.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Minimal, valid entity builders shared by the BP-61 {@code @DataJpaTest} classes,
 * so each repository test can focus on the query under test instead of re-deriving
 * "what does a persistable Business/Employee/Booking need" every time. Every
 * builder here fills in only the columns the schema actually requires
 * (see {@code V1__baseline_schema.sql} and its successors) - nothing more.
 */
final class TestFixtures {

    private TestFixtures() {
    }

    static User.UserBuilder userBuilder(String email) {
        return User.builder()
                .email(email)
                .password("bcrypt-hash-not-a-real-password")
                .role(User.UserRole.BUSINESS_ADMIN);
    }

    static Business.BusinessBuilder businessBuilder(User owner, String slug) {
        return Business.builder()
                .owner(owner)
                .name("Business " + slug)
                .slug(slug)
                .address("1 Test Street")
                .city("Chisinau")
                .phone("+37360000000");
    }

    static Employee.EmployeeBuilder employeeBuilder(Business business, String firstName) {
        return Employee.builder()
                .business(business)
                .firstName(firstName)
                .lastName("Employee");
        // NB: Employee.onCreate() (@PrePersist) unconditionally sets enabled=true on
        // insert regardless of what the builder sets - see Employee.java.
    }

    static Location.LocationBuilder locationBuilder(Business business) {
        return Location.builder()
                .business(business)
                .name("Main location")
                .city("Chisinau")
                .country("Moldova")
                .isDefaultLocation(true);
    }

    static ProvidedService.ProvidedServiceBuilder serviceBuilder(Business business, String name) {
        return ProvidedService.builder()
                .business(business)
                .name(name)
                .price(new BigDecimal("100.00"))
                .durationMinutes(30);
        // NB: ProvidedService.onCreate() (@PrePersist) unconditionally sets active=true.
    }

    static EmployeeLocationServicePrice.EmployeeLocationServicePriceBuilder priceEntryBuilder(
            Employee employee, ProvidedService service, Location location) {
        return EmployeeLocationServicePrice.builder()
                .employee(employee)
                .service(service)
                .location(location)
                .price(new BigDecimal("100.00"));
    }

    static Booking.BookingBuilder bookingBuilder(EmployeeLocationServicePrice priceEntry,
                                                  LocalDateTime start,
                                                  LocalDateTime end) {
        return Booking.builder()
                .priceEntry(priceEntry)
                .customerName("Jane Customer")
                .customerEmail("jane.customer@example.com")
                .customerPhone("+37369000000")
                .startTime(start)
                .endTime(end);
        // NB: Booking.onCreate() (@PrePersist) unconditionally sets status=CONFIRMED on
        // insert regardless of what the builder sets - tests that need a different
        // status must set it and save again *after* the initial persist.
    }
}
