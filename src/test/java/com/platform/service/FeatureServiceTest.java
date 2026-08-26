package com.platform.service;

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
}
