package com.platform.service;

import com.platform.dto.analytics.BusinessDashboardDTO;
import com.platform.entity.Business;
import com.platform.entity.User;
import com.platform.exception.ResourceNotFoundException;
import com.platform.repository.BookingRepository;
import com.platform.repository.BusinessRepository;
import com.platform.repository.ReviewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private BusinessRepository businessRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    @Test
    void getBusinessDashboard_ownerSeesCounters() {
        User owner = user(User.UserRole.BUSINESS_ADMIN);
        Business business = business(owner);

        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));
        when(bookingRepository.countCompletedByBusiness(business.getId())).thenReturn(7L);
        when(reviewRepository.getAverageRatingByBusiness(business.getId())).thenReturn(4.5);
        when(reviewRepository.countByBusinessId(business.getId())).thenReturn(3L);

        BusinessDashboardDTO dashboard = analyticsService.getBusinessDashboard(business.getId(), owner);

        assertEquals(business.getId(), dashboard.businessId());
        assertEquals(7L, dashboard.totalBookings());
        assertEquals(4.5, dashboard.averageRating());
        assertEquals(3L, dashboard.totalReviews());
    }

    @Test
    void getBusinessDashboard_nullAggregatesDefaultToZero() {
        User owner = user(User.UserRole.BUSINESS_ADMIN);
        Business business = business(owner);

        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));
        when(bookingRepository.countCompletedByBusiness(business.getId())).thenReturn(null);
        when(reviewRepository.getAverageRatingByBusiness(business.getId())).thenReturn(null);
        when(reviewRepository.countByBusinessId(business.getId())).thenReturn(0L);

        BusinessDashboardDTO dashboard = analyticsService.getBusinessDashboard(business.getId(), owner);

        assertEquals(0L, dashboard.totalBookings());
        assertEquals(0.0, dashboard.averageRating());
    }

    @Test
    void getBusinessDashboard_otherTenantGets404() {
        User owner = user(User.UserRole.BUSINESS_ADMIN);
        User stranger = user(User.UserRole.BUSINESS_ADMIN);
        Business business = business(owner);

        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));

        assertThrows(ResourceNotFoundException.class,
                () -> analyticsService.getBusinessDashboard(business.getId(), stranger));

        verifyNoInteractions(bookingRepository);
        verifyNoInteractions(reviewRepository);
    }

    @Test
    void getBusinessDashboard_platformAdminSeesAnyBusiness() {
        User owner = user(User.UserRole.BUSINESS_ADMIN);
        User admin = user(User.UserRole.PLATFORM_ADMIN);
        Business business = business(owner);

        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));
        when(bookingRepository.countCompletedByBusiness(business.getId())).thenReturn(1L);
        when(reviewRepository.getAverageRatingByBusiness(business.getId())).thenReturn(5.0);
        when(reviewRepository.countByBusinessId(business.getId())).thenReturn(1L);

        assertNotNull(analyticsService.getBusinessDashboard(business.getId(), admin));
    }

    @Test
    void getBusinessDashboard_unknownBusinessGets404() {
        UUID missing = UUID.randomUUID();
        when(businessRepository.findById(missing)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> analyticsService.getBusinessDashboard(missing, user(User.UserRole.BUSINESS_ADMIN)));
    }

    private User user(User.UserRole role) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setRole(role);
        return user;
    }

    private Business business(User owner) {
        return Business.builder()
                .id(UUID.randomUUID())
                .owner(owner)
                .build();
    }
}
