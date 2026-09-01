package com.platform.service;

import com.platform.dto.business.BusinessFeatureDTO;
import com.platform.entity.Business;
import com.platform.entity.BusinessFeature;
import com.platform.entity.User;
import com.platform.exception.BadRequestException;
import com.platform.exception.BusinessFeatureAlreadyExistsException;
import com.platform.exception.BusinessOwnershipException;
import com.platform.exception.ResourceNotFoundException;
import com.platform.repository.BusinessFeatureRepository;
import com.platform.repository.BusinessRepository;
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
class FeatureServiceTest {

    @Mock
    private BusinessFeatureRepository featureRepository;

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private FeatureService featureService;

    // ==================== getFeatureById ====================

    @Test
    void getFeatureById_notFound_throwsNotFound() {
        when(featureRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> featureService.getFeatureById(1L));
    }

    // ==================== getAllFeatures ====================

    // A missing business used to throw BusinessException, which maps to 403 - the wrong
    // answer for something that simply is not there.
    @Test
    void getAllFeatures_businessNotFound_throwsNotFound() {
        UUID businessId = UUID.randomUUID();

        when(businessRepository.findById(businessId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> featureService.getAllFeatures(businessId));

        verify(featureRepository, never()).findByBusinessId(any());
    }

    // ==================== addFeature ====================

    // The path variable is the authority; the body's businessId used to decide
    // which business was written to, so URL and effect could disagree.
    @Test
    void addFeature_usesPathBusinessIdNotBody() {
        User owner = enabledUser();
        Business business = business(owner);
        UUID otherBusinessId = UUID.randomUUID();

        when(userService.getUser()).thenReturn(owner);

        BusinessFeatureDTO request = BusinessFeatureDTO.builder()
                .businessId(otherBusinessId)
                .name("Wi-Fi")
                .build();

        assertThrows(BadRequestException.class,
                () -> featureService.addFeature(business.getId(), request));

        verify(featureRepository, never()).save(any());
    }

    @Test
    void addFeature_bodyWithoutBusinessId_writesToPathBusiness() {
        User owner = enabledUser();
        Business business = business(owner);

        when(userService.getUser()).thenReturn(owner);
        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));
        when(featureRepository.existsByBusinessIdAndName(business.getId(), "Wi-Fi")).thenReturn(false);

        BusinessFeatureDTO response = featureService.addFeature(
                business.getId(),
                BusinessFeatureDTO.builder().name("Wi-Fi").build());

        assertEquals(business.getId(), response.getBusinessId());
        verify(featureRepository).save(any(BusinessFeature.class));
    }

    // BP-54: BusinessFeature.name is unique per business, not platform-wide (see
    // V10__scope_business_feature_name_uniqueness.sql), but a duplicate within the SAME
    // business must still be rejected - via this pre-check, not the raw
    // DataIntegrityViolationException the removed column-level unique constraint used to
    // produce. The cross-business case (two different businesses both using "Wi-Fi") is a
    // schema fact this Mockito-only test cannot exercise - only a real database can prove
    // that a second business's insert no longer collides with the first.
    @Test
    void addFeature_duplicateWithinSameBusiness_throwsAlreadyExists() {
        User owner = enabledUser();
        Business business = business(owner);

        when(userService.getUser()).thenReturn(owner);
        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));
        when(featureRepository.existsByBusinessIdAndName(business.getId(), "Wi-Fi")).thenReturn(true);

        assertThrows(BusinessFeatureAlreadyExistsException.class,
                () -> featureService.addFeature(business.getId(), BusinessFeatureDTO.builder().name("Wi-Fi").build()));

        verify(featureRepository, never()).save(any());
    }

    // ==================== removeFeature ====================

    // removeFeature had no ownership check at all: any BUSINESS_ADMIN could delete
    // any other business's features.
    @Test
    void removeFeature_notOwner_throwsOwnership() {
        User owner = enabledUser();
        User stranger = enabledUser();
        Business business = business(owner);

        when(userService.getUser()).thenReturn(stranger);
        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));

        assertThrows(BusinessOwnershipException.class,
                () -> featureService.removeFeature(business.getId(), 1L));

        verify(featureRepository, never()).delete(any());
    }

    @Test
    void removeFeature_featureFromAnotherBusiness_throwsNotFound() {
        User owner = enabledUser();
        Business business = business(owner);
        Business otherBusiness = business(owner);

        when(userService.getUser()).thenReturn(owner);
        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));
        when(featureRepository.findById(1L)).thenReturn(Optional.of(feature(otherBusiness)));

        assertThrows(ResourceNotFoundException.class,
                () -> featureService.removeFeature(business.getId(), 1L));

        verify(featureRepository, never()).delete(any());
    }

    @Test
    void removeFeature_owner_deletes() {
        User owner = enabledUser();
        Business business = business(owner);
        BusinessFeature feature = feature(business);

        when(userService.getUser()).thenReturn(owner);
        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));
        when(featureRepository.findById(1L)).thenReturn(Optional.of(feature));

        featureService.removeFeature(business.getId(), 1L);

        verify(featureRepository).delete(feature);
    }

    // ==================== helpers ====================

    private User enabledUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setRole(User.UserRole.BUSINESS_ADMIN);
        user.setEnabled(true);
        return user;
    }

    private Business business(User owner) {
        return Business.builder()
                .id(UUID.randomUUID())
                .owner(owner)
                .build();
    }

    private BusinessFeature feature(Business business) {
        return BusinessFeature.builder()
                .featureId(1L)
                .business(business)
                .name("Wi-Fi")
                .build();
    }
}
