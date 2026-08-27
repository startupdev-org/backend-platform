package com.platform.service;

import com.platform.dto.analytics.BusinessDashboardDTO;
import com.platform.entity.Business;
import com.platform.entity.User;
import com.platform.exception.ResourceNotFoundException;
import com.platform.repository.BookingRepository;
import com.platform.repository.BusinessRepository;
import com.platform.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final BookingRepository bookingRepository;
    private final ReviewRepository reviewRepository;
    private final BusinessRepository businessRepository;

    private static final String BUSINESS_NOT_FOUND = "Business not found";

    /**
     * Dashboard counters for one business.
     *
     * <p>A business the caller does not own is reported as not found rather than
     * forbidden: the dashboard is competitor-sensitive, so a 403 would confirm
     * that a probed business ID exists. {@code PLATFORM_ADMIN} sees any business.
     */
    @Transactional(readOnly = true)
    public BusinessDashboardDTO getBusinessDashboard(UUID businessId, User currentUser) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException(BUSINESS_NOT_FOUND));

        if (business.isNotOwner(currentUser)
                && !currentUser.getRole().equals(User.UserRole.PLATFORM_ADMIN)) {
            throw new ResourceNotFoundException(BUSINESS_NOT_FOUND);
        }

        Long totalBookings = bookingRepository.countCompletedByBusiness(businessId);
        Double averageRating = reviewRepository.getAverageRatingByBusiness(businessId);

        return BusinessDashboardDTO.builder()
                .businessId(businessId)
                .totalBookings(totalBookings != null ? totalBookings : 0L)
                .averageRating(averageRating != null ? averageRating : 0.0)
                .totalReviews(reviewRepository.countByBusinessId(businessId))
                .build();
    }
}
