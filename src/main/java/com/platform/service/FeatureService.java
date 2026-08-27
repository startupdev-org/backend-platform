package com.platform.service;

import com.platform.dto.business.BusinessFeatureDTO;
import com.platform.entity.Business;
import com.platform.entity.BusinessFeature;
import com.platform.entity.User;
import com.platform.exception.BadRequestException;
import com.platform.exception.BusinessFeatureAlreadyExistsException;
import com.platform.exception.BusinessOwnershipException;
import com.platform.exception.ResourceNotFoundException;
import com.platform.exception.UserNotEnabledException;
import com.platform.repository.BusinessFeatureRepository;
import com.platform.repository.BusinessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FeatureService {

    private final BusinessFeatureRepository featureRepository;
    private final BusinessRepository businessRepository;
    private final UserService userService;

    public Set<BusinessFeatureDTO> getAllFeatures(UUID businessId) {
        Business business = getBusinessById(businessId);

        return featureRepository.findByBusinessId(business.getId())
                .stream()
                .map(FeatureService::toDTO)
                .collect(Collectors.toSet());
    }

    /**
     * Features for many businesses in one query, grouped by business id. The list
     * endpoints call this once instead of {@link #getAllFeatures(UUID)} per
     * business. See BP-53.
     */
    public Map<UUID, Set<BusinessFeatureDTO>> getFeaturesByBusinessIds(Collection<UUID> businessIds) {
        if (businessIds.isEmpty()) return Map.of();
        return featureRepository.findByBusinessIdIn(businessIds).stream()
                .collect(Collectors.groupingBy(
                        f -> f.getBusiness().getId(),
                        Collectors.mapping(FeatureService::toDTO, Collectors.toSet())));
    }

    private static BusinessFeatureDTO toDTO(BusinessFeature f) {
        return BusinessFeatureDTO.builder()
                .featureId(f.getFeatureId())
                .businessId(f.getBusiness().getId())
                .name(f.getName())
                .build();
    }

    /**
     * Adds a feature to {@code businessId}.
     *
     * <p>The path variable is the authority for which business is written to. The
     * request body used to decide that instead, which let the URL and the effect
     * disagree and made the SecurityConfig matcher for this path meaningless.
     */
    @Transactional
    public BusinessFeatureDTO addFeature(UUID businessId, BusinessFeatureDTO request) {
        User user = userService.getUser();

        if (!user.isEnabled())
            throw new UserNotEnabledException("User is not enabled");

        if (request.getBusinessId() != null && !request.getBusinessId().equals(businessId)) {
            throw new BadRequestException("businessId in the body does not match the one in the path");
        }

        Business business = getBusinessById(businessId);

        if (business.isNotOwner(user)) {
            throw new BusinessOwnershipException("Cannot add a new feature to a business you do not own");
        }

        if(featureRepository.existsByBusinessIdAndName(businessId, request.getName())) {
            throw new BusinessFeatureAlreadyExistsException(
                    "Feature already exists for this business"
            );
        }

        BusinessFeature feature = BusinessFeature.builder()
                .business(business)
                .name(request.getName())
                .build();

        featureRepository.save(feature);

        return BusinessFeatureDTO.builder()
                .featureId(feature.getFeatureId())
                .businessId(feature.getBusiness().getId())
                .name(feature.getName())
                .build();

    }

    /**
     * Deletes a feature from {@code businessId}.
     *
     * <p>Ownership is checked first, mirroring {@link #addFeature}: this method used
     * to verify only that the feature belonged to the business named in the path, so
     * any BUSINESS_ADMIN could delete any other business's features. A feature that
     * belongs to a different business is reported as not found, not forbidden.
     */
    @Transactional
    public void removeFeature(UUID businessId, Long featureId) {
        User user = userService.getUser();

        Business business = getBusinessById(businessId);

        if (business.isNotOwner(user)) {
            throw new BusinessOwnershipException("Cannot remove a feature from a business you do not own");
        }

        BusinessFeature feature = getFeatureById(featureId);

        if (!business.getId().equals(feature.getBusiness().getId())) {
            throw new ResourceNotFoundException("Feature not found");
        }

        featureRepository.delete(feature);
    }

    public BusinessFeature getFeatureById(Long featureId) {
        return featureRepository.findById(featureId)
                .orElseThrow(() -> new ResourceNotFoundException("Feature not found"));
    }

    private Business getBusinessById(UUID id) {
        return businessRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));
    }
}
