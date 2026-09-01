package com.platform.integration;

import com.platform.entity.Booking;
import com.platform.entity.Business;
import com.platform.entity.Employee;
import com.platform.entity.EmployeeLocationServicePrice;
import com.platform.entity.Location;
import com.platform.entity.ProvidedService;
import com.platform.entity.Review;
import com.platform.entity.User;
import com.platform.repository.ReviewRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;

/**
 * Covers {@link ReviewRepository}'s three {@code @Query} methods. All three walk
 * the same four-hop path - {@code review.booking.priceEntry.employee.business.id}
 * - to scope a review to a business, since {@code Review} has no direct FK to
 * {@code Business}. That path is exactly the kind of JPQL Mockito cannot verify:
 * it compiles against the entity graph, not against the database, so a wrong hop
 * would only be caught here, against a real schema.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class ReviewRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ReviewRepository reviewRepository;

    private EmployeeLocationServicePrice priceEntryFor(String slugSuffix) {
        User owner = entityManager.persistFlushFind(
                TestFixtures.userBuilder("owner-" + UUID.randomUUID() + "@example.com").build());
        Business business = entityManager.persistFlushFind(
                TestFixtures.businessBuilder(owner, "business-" + slugSuffix + "-" + UUID.randomUUID()).build());
        Employee employee = entityManager.persistFlushFind(TestFixtures.employeeBuilder(business, "Alex").build());
        Location location = entityManager.persistFlushFind(TestFixtures.locationBuilder(business).build());
        ProvidedService service = entityManager.persistFlushFind(
                TestFixtures.serviceBuilder(business, "Haircut").build());
        return entityManager.persistFlushFind(
                TestFixtures.priceEntryBuilder(employee, service, location).build());
    }

    private Booking bookingFor(EmployeeLocationServicePrice priceEntry, int hourOffset) {
        LocalDateTime start = LocalDateTime.of(2026, 9, 10, 9, 0).plusHours(hourOffset);
        return entityManager.persistFlushFind(
                TestFixtures.bookingBuilder(priceEntry, start, start.plusMinutes(30)).build());
    }

    private Review reviewFor(Booking booking, double ratingOverall) {
        Review review = Review.builder().booking(booking).ratingOverall(ratingOverall).build();
        return entityManager.persistFlushFind(review);
    }

    @Test
    void findByBusinessId_countByBusinessId_scopeToOnlyThatBusinessAcrossTheFourHopJoin() {
        EmployeeLocationServicePrice priceA = priceEntryFor("a");
        EmployeeLocationServicePrice priceB = priceEntryFor("b");
        UUID businessAId = priceA.getEmployee().getBusiness().getId();
        UUID businessBId = priceB.getEmployee().getBusiness().getId();

        Review reviewA = reviewFor(bookingFor(priceA, 0), 4.0);
        reviewFor(bookingFor(priceB, 1), 5.0);

        List<Review> foundForA = reviewRepository.findByBusinessId(businessAId);
        assertEquals(List.of(reviewA.getId()), foundForA.stream().map(Review::getId).toList());
        assertEquals(4.0, foundForA.get(0).getRatingOverall());

        assertEquals(1L, reviewRepository.countByBusinessId(businessAId));
        assertEquals(1L, reviewRepository.countByBusinessId(businessBId));
        assertEquals(0L, reviewRepository.countByBusinessId(UUID.randomUUID()));
    }

    @Test
    void getAverageRatingByBusiness_averagesOnlyThatBusinesssReviews() {
        EmployeeLocationServicePrice priceA = priceEntryFor("avg-a");
        EmployeeLocationServicePrice priceB = priceEntryFor("avg-b");
        UUID businessAId = priceA.getEmployee().getBusiness().getId();

        reviewFor(bookingFor(priceA, 0), 4.0);
        reviewFor(bookingFor(priceA, 1), 2.0);
        // Different business, wildly different rating - must not leak into A's average.
        reviewFor(bookingFor(priceB, 0), 1.0);

        Double average = reviewRepository.getAverageRatingByBusiness(businessAId);
        assertTrue(Math.abs(3.0 - average) < 0.0001, "expected (4.0 + 2.0) / 2 = 3.0, got " + average);
    }

    @Test
    void getAverageRatingByBusiness_returnsNullWhenTheBusinessHasNoReviews() {
        EmployeeLocationServicePrice priceEntry = priceEntryFor("no-reviews");
        UUID businessId = priceEntry.getEmployee().getBusiness().getId();

        assertNull(reviewRepository.getAverageRatingByBusiness(businessId));
    }
}
