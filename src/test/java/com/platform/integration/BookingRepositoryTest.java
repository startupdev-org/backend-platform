package com.platform.integration;

import com.platform.entity.Booking;
import com.platform.entity.Business;
import com.platform.entity.Employee;
import com.platform.entity.EmployeeLocationServicePrice;
import com.platform.entity.Location;
import com.platform.entity.ProvidedService;
import com.platform.entity.User;
import com.platform.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;

/**
 * Covers {@link BookingRepository}'s hand-written JPQL against a real Postgres -
 * the fetch-graph queries backing BP-53 (N+1 fanout) and the date-range query
 * that {@code AvailabilityService} filters through to compute booking overlap
 * (see class comment on {@code BookingRepository.LIST_FETCH_GRAPH} and
 * {@code AvailabilityService.isFree}). Mockito, used everywhere else in this
 * repo, cannot exercise real JPQL joins or aggregate functions - only a real
 * JPA provider against a real schema can.
 *
 * <p>{@code @AutoConfigureTestDatabase(Replace.NONE)} is required: {@code @DataJpaTest}
 * replaces the configured datasource with an embedded one by default, which would
 * silently discard the Testcontainers Postgres wired in by
 * {@link AbstractIntegrationTest}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class BookingRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private BookingRepository bookingRepository;

    private Business business;
    private Employee employee;
    private EmployeeLocationServicePrice priceEntry;

    @BeforeEach
    void setUp() {
        User owner = entityManager.persistFlushFind(
                TestFixtures.userBuilder("owner-" + UUID.randomUUID() + "@example.com").build());
        business = entityManager.persistFlushFind(
                TestFixtures.businessBuilder(owner, "business-" + UUID.randomUUID()).build());
        employee = entityManager.persistFlushFind(
                TestFixtures.employeeBuilder(business, "Alex").build());
        Location location = entityManager.persistFlushFind(TestFixtures.locationBuilder(business).build());
        ProvidedService service = entityManager.persistFlushFind(
                TestFixtures.serviceBuilder(business, "Haircut").build());
        priceEntry = entityManager.persistFlushFind(
                TestFixtures.priceEntryBuilder(employee, service, location).build());
    }

    private Booking persistBooking(LocalDateTime start, LocalDateTime end) {
        return entityManager.persistFlushFind(TestFixtures.bookingBuilder(priceEntry, start, end).build());
    }

    private Booking persistBookingWithStatus(LocalDateTime start, LocalDateTime end, Booking.BookingStatus status) {
        Booking booking = persistBooking(start, end);
        // Booking.onCreate() forces status=CONFIRMED on insert - flip it with a
        // separate update, exactly like BookingService does after the initial save.
        booking.setStatus(status);
        entityManager.getEntityManager().flush();
        return entityManager.find(Booking.class, booking.getId());
    }

    @Test
    void findAllForListing_eagerlyLoadsTheWholeGraphSoDetachedAccessDoesNotThrow() {
        persistBooking(LocalDateTime.of(2026, 9, 10, 10, 0), LocalDateTime.of(2026, 9, 10, 10, 30));

        List<Booking> results = bookingRepository.findAllForListing();
        assertEquals(1, results.size());

        // Detach before reading the associations. If LIST_FETCH_GRAPH's JOIN FETCH
        // did not actually eager-load employee/service, these would be unresolved
        // lazy proxies and throw LazyInitializationException once detached - the
        // exact N+1 regression BP-53 fixed.
        Booking booking = results.get(0);
        entityManager.getEntityManager().detach(booking);

        assertDoesNotThrow(() -> {
            assertEquals("Alex", booking.getPriceEntry().getEmployee().getFirstName());
            assertEquals("Haircut", booking.getPriceEntry().getService().getName());
        });
    }

    @Test
    void findByEmployeeAndDateRange_returnsOnlyBookingsStartingInsideTheHalfOpenWindow() {
        LocalDateTime dayStart = LocalDateTime.of(2026, 9, 10, 0, 0);
        LocalDateTime dayEnd = dayStart.plusDays(1);

        Booking before = persistBooking(dayStart.minusHours(1), dayStart.minusMinutes(30));
        Booking inside = persistBooking(dayStart.plusHours(9), dayStart.plusHours(10));
        // Starts exactly on the exclusive upper bound - must NOT be included.
        Booking onBoundary = persistBooking(dayEnd, dayEnd.plusMinutes(30));

        List<Booking> found = bookingRepository.findByEmployeeAndDateRange(employee.getId(), dayStart, dayEnd);

        List<UUID> foundIds = found.stream().map(Booking::getId).toList();
        assertEquals(List.of(inside.getId()), foundIds);
        assertTrue(!foundIds.contains(before.getId()) && !foundIds.contains(onBoundary.getId()));
    }

    @Test
    void countCompletedByBusiness_countsOnlyCompletedStatusForThatBusiness() {
        persistBookingWithStatus(
                LocalDateTime.of(2026, 9, 10, 9, 0), LocalDateTime.of(2026, 9, 10, 9, 30),
                Booking.BookingStatus.COMPLETED);
        persistBookingWithStatus(
                LocalDateTime.of(2026, 9, 11, 9, 0), LocalDateTime.of(2026, 9, 11, 9, 30),
                Booking.BookingStatus.COMPLETED);
        // Left CONFIRMED (Booking.onCreate()'s default) - must not be counted.
        persistBooking(LocalDateTime.of(2026, 9, 12, 9, 0), LocalDateTime.of(2026, 9, 12, 9, 30));

        assertEquals(2L, bookingRepository.countCompletedByBusiness(business.getId()));
    }

    @Test
    void findByBusinessIdInForListing_scopesToTheGivenBusinessIds() {
        persistBooking(LocalDateTime.of(2026, 9, 10, 9, 0), LocalDateTime.of(2026, 9, 10, 9, 30));

        assertEquals(1, bookingRepository.findByBusinessIdInForListing(List.of(business.getId())).size());
        assertEquals(0, bookingRepository.findByBusinessIdInForListing(List.of(UUID.randomUUID())).size());
    }

    @Test
    void findByProvidedServiceId_followsThePriceEntryToTheService() {
        Booking booking = persistBooking(
                LocalDateTime.of(2026, 9, 10, 9, 0), LocalDateTime.of(2026, 9, 10, 9, 30));

        List<Booking> found = bookingRepository.findByProvidedServiceId(priceEntry.getService().getId());

        assertEquals(List.of(booking.getId()), found.stream().map(Booking::getId).toList());
    }
}
