package com.platform.service;

import com.platform.dto.review.ReviewRequestDTO;
import com.platform.dto.review.ReviewResponseDTO;
import com.platform.entity.Booking;
import com.platform.entity.Business;
import com.platform.entity.Review;
import com.platform.entity.User;
import com.platform.exception.BusinessException;
import com.platform.exception.ResourceNotFoundException;
import com.platform.repository.BookingRepository;
import com.platform.repository.ReviewRepository;
import com.platform.utils.BusinessOwnershipValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;

    @Transactional
    public ReviewResponseDTO createReview(UUID bookingId, ReviewRequestDTO dto) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        if (!booking.getStatus().equals(Booking.BookingStatus.COMPLETED)) {
            throw new BusinessException("Review can only be added for completed bookings");
        }

        if (reviewRepository.findByBookingId(bookingId).isPresent()) {
            throw new BusinessException("Review already exists for this booking");
        }

        Review review = Review.builder()
                .ratingOverall(dto.getRatingOverall())
                .ratingCleanliness(dto.getRatingCleanliness())
                .ratingService(dto.getRatingService())
                .ratingPrice(dto.getRatingPrice())
                .comment(dto.getComment())
                .booking(booking)
                .build();

        review = reviewRepository.save(review);
        return toDTO(review);
    }

    public ReviewResponseDTO getReview(UUID id) {
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));
        return toDTO(review);
    }

    public List<ReviewResponseDTO> getBusinessReviews(UUID businessId) {
        return reviewRepository.findByBusinessId(businessId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public ReviewResponseDTO addBusinessReply(UUID id, String reply, User currentUser) {
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));

        Business business = review.getBooking().getEmployee().getBusiness();
        BusinessOwnershipValidator.assertOwner(business, currentUser);

        review.setBusinessReply(reply);
        review = reviewRepository.save(review);
        return toDTO(review);
    }

    public Double getAverageRating(UUID businessId) {
        Double average = reviewRepository.getAverageRatingByBusiness(businessId);
        return average != null ? average : 0.0;
    }

    private ReviewResponseDTO toDTO(Review review) {
        return ReviewResponseDTO.builder()
                .id(review.getId())
                .ratingOverall(review.getRatingOverall())
                .ratingCleanliness(review.getRatingCleanliness())
                .ratingService(review.getRatingService())
                .ratingPrice(review.getRatingPrice())
                .comment(review.getComment())
                .businessReply(review.getBusinessReply())
                .bookingId(review.getBooking().getId())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }
}
