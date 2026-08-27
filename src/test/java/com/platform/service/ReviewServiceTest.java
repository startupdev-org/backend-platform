package com.platform.service;

import com.platform.dto.review.ReviewRequestDTO;
import com.platform.dto.review.ReviewResponseDTO;
import com.platform.entity.Booking;
import com.platform.entity.Business;
import com.platform.entity.Employee;
import com.platform.entity.EmployeeLocationServicePrice;
import com.platform.entity.Review;
import com.platform.entity.User;
import com.platform.exception.BusinessException;
import com.platform.exception.ResourceNotFoundException;
import com.platform.repository.BookingRepository;
import com.platform.repository.ReviewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @InjectMocks
    private ReviewService reviewService;

    @Mock private ReviewRepository reviewRepository;
    @Mock private BookingRepository bookingRepository;

    // ── createReview: the COMPLETED gate ──────────────────────────────────────

    @Test
    void createReview_savesWhenTheBookingIsCompleted() {
        Booking booking = booking(Booking.BookingStatus.COMPLETED, business(user(User.UserRole.BUSINESS_ADMIN)));
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(reviewRepository.findByBookingId(booking.getId())).thenReturn(Optional.empty());
        when(reviewRepository.save(any(Review.class))).thenAnswer(i -> {
            Review saved = i.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        ReviewResponseDTO response = reviewService.createReview(booking.getId(), request());

        ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).save(captor.capture());
        assertEquals(booking, captor.getValue().getBooking());
        assertEquals(5.0, captor.getValue().getRatingOverall());
        assertEquals("Great", response.getComment());
        assertEquals(booking.getId(), response.getBookingId());
    }

    /**
     * A review is a statement that the service actually happened. Any status other than
     * COMPLETED must be refused, or a customer could review an appointment they cancelled
     * or one that has not taken place yet.
     */
    @ParameterizedTest
    @EnumSource(value = Booking.BookingStatus.class, names = "COMPLETED", mode = EnumSource.Mode.EXCLUDE)
    void createReview_rejectsAnyStatusOtherThanCompleted(Booking.BookingStatus status) {
        Booking booking = booking(status, business(user(User.UserRole.BUSINESS_ADMIN)));
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        assertThrows(BusinessException.class,
                () -> reviewService.createReview(booking.getId(), request()));

        verify(reviewRepository, never()).save(any());
    }

    @Test
    void createReview_rejectsASecondReviewForTheSameBooking() {
        Booking booking = booking(Booking.BookingStatus.COMPLETED, business(user(User.UserRole.BUSINESS_ADMIN)));
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(reviewRepository.findByBookingId(booking.getId()))
                .thenReturn(Optional.of(Review.builder().id(UUID.randomUUID()).booking(booking).build()));

        assertThrows(BusinessException.class,
                () -> reviewService.createReview(booking.getId(), request()));

        verify(reviewRepository, never()).save(any());
    }

    @Test
    void createReview_rejectsAnUnknownBooking() {
        UUID missing = UUID.randomUUID();
        when(bookingRepository.findById(missing)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> reviewService.createReview(missing, request()));
    }

    // ── addBusinessReply: the ownership walk ──────────────────────────────────

    /**
     * The check walks review -> booking -> employee -> business -> owner. Four hops, each
     * one a chance to reply to a review left for somebody else's business.
     */
    @Test
    void addBusinessReply_ownerOfTheReviewedBusinessMayReply() {
        User owner = user(User.UserRole.BUSINESS_ADMIN);
        Review review = reviewFor(business(owner));
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));
        when(reviewRepository.save(review)).thenReturn(review);

        ReviewResponseDTO response = reviewService.addBusinessReply(review.getId(), "Thank you", owner);

        assertEquals("Thank you", response.getBusinessReply());
        assertEquals("Thank you", review.getBusinessReply());
    }

    @Test
    void addBusinessReply_anotherBusinessOwnerMayNot() {
        User owner = user(User.UserRole.BUSINESS_ADMIN);
        User stranger = user(User.UserRole.BUSINESS_ADMIN);
        Review review = reviewFor(business(owner));
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));

        assertThrows(BusinessException.class,
                () -> reviewService.addBusinessReply(review.getId(), "Not mine", stranger));

        verify(reviewRepository, never()).save(any());
    }

    @Test
    void addBusinessReply_platformAdminMayReplyToAnyReview() {
        User owner = user(User.UserRole.BUSINESS_ADMIN);
        User admin = user(User.UserRole.PLATFORM_ADMIN);
        Review review = reviewFor(business(owner));
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));
        when(reviewRepository.save(review)).thenReturn(review);

        assertEquals("Handled", reviewService.addBusinessReply(review.getId(), "Handled", admin)
                .getBusinessReply());
    }

    @Test
    void addBusinessReply_rejectsAnUnknownReview() {
        UUID missing = UUID.randomUUID();
        when(reviewRepository.findById(missing)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> reviewService.addBusinessReply(missing, "Hello", user(User.UserRole.PLATFORM_ADMIN)));
    }

    // ── Reads ─────────────────────────────────────────────────────────────────

    @Test
    void getReview_returnsTheReview() {
        Review review = reviewFor(business(user(User.UserRole.BUSINESS_ADMIN)));
        review.setComment("Nice");
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));

        assertEquals("Nice", reviewService.getReview(review.getId()).getComment());
    }

    @Test
    void getReview_rejectsAnUnknownReview() {
        UUID missing = UUID.randomUUID();
        when(reviewRepository.findById(missing)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> reviewService.getReview(missing));
    }

    @Test
    void getBusinessReviews_mapsEveryReviewToADto() {
        Business business = business(user(User.UserRole.BUSINESS_ADMIN));
        when(reviewRepository.findByBusinessId(business.getId()))
                .thenReturn(List.of(reviewFor(business), reviewFor(business)));

        assertEquals(2, reviewService.getBusinessReviews(business.getId()).size());
    }

    @Test
    void getBusinessReviews_returnsEmptyWhenThereAreNone() {
        UUID businessId = UUID.randomUUID();
        when(reviewRepository.findByBusinessId(businessId)).thenReturn(List.of());

        assertEquals(List.of(), reviewService.getBusinessReviews(businessId));
    }

    // ── Average rating ────────────────────────────────────────────────────────

    @Test
    void getAverageRating_returnsTheAggregate() {
        UUID businessId = UUID.randomUUID();
        when(reviewRepository.getAverageRatingByBusiness(businessId)).thenReturn(4.25);

        assertEquals(4.25, reviewService.getAverageRating(businessId));
    }

    /**
     * AVG() over no rows is null in SQL, not zero. Returning that null would put a null
     * rating on the booking page for every business that has not been reviewed yet.
     */
    @Test
    void getAverageRating_returnsZeroWhenThereAreNoReviews() {
        UUID businessId = UUID.randomUUID();
        when(reviewRepository.getAverageRatingByBusiness(businessId)).thenReturn(null);

        assertEquals(0.0, reviewService.getAverageRating(businessId));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User user(User.UserRole role) {
        return User.builder().id(UUID.randomUUID()).email("u@x.io").role(role).build();
    }

    private Business business(User owner) {
        return Business.builder().id(UUID.randomUUID()).owner(owner).build();
    }

    private Booking booking(Booking.BookingStatus status, Business business) {
        Employee employee = Employee.builder().id(UUID.randomUUID()).business(business).build();
        EmployeeLocationServicePrice price = EmployeeLocationServicePrice.builder()
                .id(UUID.randomUUID()).employee(employee).build();
        return Booking.builder()
                .id(UUID.randomUUID())
                .status(status)
                .priceEntry(price)
                .build();
    }

    private Review reviewFor(Business business) {
        return Review.builder()
                .id(UUID.randomUUID())
                .ratingOverall(5.0)
                .booking(booking(Booking.BookingStatus.COMPLETED, business))
                .build();
    }

    private ReviewRequestDTO request() {
        ReviewRequestDTO dto = new ReviewRequestDTO();
        dto.setRatingOverall(5.0);
        dto.setRatingCleanliness(4.0);
        dto.setRatingService(5.0);
        dto.setRatingPrice(4.0);
        dto.setComment("Great");
        return dto;
    }
}
