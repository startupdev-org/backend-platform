package com.platform.service;

import com.platform.dto.business.BusinessFeatureDTO;
import com.platform.dto.business.BusinessMapper;
import com.platform.dto.business.BusinessRequestDTO;
import com.platform.dto.business.BusinessResponseDTO;
import com.platform.dto.employee.EmployeeResponseDTO;
import com.platform.dto.location.LocationResponseDTO;
import com.platform.dto.service.ServiceResponseDTO;
import com.platform.entity.Business;
import com.platform.entity.BusinessCategoryType;
import com.platform.entity.User;
import com.platform.exception.BadRequestException;
import com.platform.exception.BusinessException;
import com.platform.exception.ResourceNotFoundException;
import com.platform.repository.BusinessRepository;
import com.platform.repository.spec.BusinessSpecifications;
import com.platform.storage.ImageUrlResolver;
import com.platform.utils.SlugGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class BusinessService {

    private final BusinessRepository businessRepository;
    private final UserService userService;
    private final ProvidedServicesService providedServicesService;
    private final EmployeeService employeeService;
    private final FeatureService featureService;
    private final LocationService locationService;
    private final ImageUrlResolver imageUrls;

    private static final String BUSINESS_EXCEPTION = "Business not found";

    /** Whitelisted {@code sort} values for the business list endpoints. */
    public static final Set<String> SORTABLE_FIELDS =
            Set.of("name", "city", "ratingOverall", "createdAt", "updatedAt");
    public static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    @Transactional
    public BusinessResponseDTO createBusiness(BusinessRequestDTO dto) {
        User owner = getUser();

        String slug = SlugGenerator.generate(dto.getName());

        if (!owner.getRole().equals(User.UserRole.BUSINESS_ADMIN))
            throw new BusinessException("Just business admin can create new businesses");

        Business business = Business.builder()
                .name(dto.getName())
                .slug(slug)
                .description(dto.getDescription())
                .address(dto.getAddress())
                .city(dto.getCity())
                .phone(dto.getPhone())
                .website(dto.getWebsite())
                .owner(owner)
                .build();

        business = businessRepository.save(business);
        return toDTO(business);
    }

    public BusinessResponseDTO getBusinessDTOById(UUID id) {
        Business business = businessRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(BUSINESS_EXCEPTION));
        return toDTO(business);
    }

    public Business getBusinessById(UUID id) {
        return businessRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(BUSINESS_EXCEPTION));
    }

    public BusinessResponseDTO getBusinessBySlug(String slug) {
        Business business = businessRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException(BUSINESS_EXCEPTION));
        return toDTO(business);
    }

    /**
     * Lists businesses, applying every filter that was supplied.
     *
     * <p>This used to be an if / else-if chain, so at most one filter reached the
     * query: city plus category dropped the category, and minRating on its own was
     * ignored entirely. Silently returning unfiltered rows is worse than refusing
     * the request, because the caller believes the results are filtered.
     *
     * <p>Note that minRating can only match once {@code Business.ratingOverall} is
     * actually maintained - it is still 0.0 for every row (BP-56).
     */
    public Page<BusinessResponseDTO> listBusinesses(String city, Double minRating, String businessCategoryType, Pageable pageable) {
        Specification<Business> spec = Specification.where(null);

        if (city != null && !city.isBlank()) {
            spec = spec.and(BusinessSpecifications.cityContains(city));
        }
        if (minRating != null) {
            spec = spec.and(BusinessSpecifications.ratingAtLeast(minRating));
        }
        if (businessCategoryType != null && !businessCategoryType.isBlank()) {
            spec = spec.and(BusinessSpecifications.hasCategory(parseCategory(businessCategoryType)));
        }

        return businessRepository.findAll(spec, pageable).map(this::toDTO);
    }

    /** An unknown category is rejected rather than quietly matching nothing. */
    private BusinessCategoryType parseCategory(String value) {
        try {
            return BusinessCategoryType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unknown businessCategory: " + value);
        }
    }

    @Transactional
    public BusinessResponseDTO updateBusiness(UUID id, BusinessRequestDTO dto) {
        Business business = businessRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(BUSINESS_EXCEPTION));

        User currentUser = getUser();

        if (!business.getOwner().getId().equals(currentUser.getId()) &&
            !currentUser.getRole().equals(User.UserRole.PLATFORM_ADMIN)) {
            throw new BusinessException("Unauthorized");
        }

        business.setName(dto.getName());
        business.setDescription(dto.getDescription());
        business.setAddress(dto.getAddress());
        business.setCity(dto.getCity());
        business.setPhone(dto.getPhone());
        business.setWebsite(dto.getWebsite());

        business = businessRepository.save(business);
        return toDTO(business);
    }

    @Transactional
    public void deleteBusiness(UUID id, User currentUser) {
        Business business = businessRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(BUSINESS_EXCEPTION));

        if (!business.getOwner().getId().equals(currentUser.getId()) &&
            !currentUser.getRole().equals(User.UserRole.PLATFORM_ADMIN)) {
            throw new BusinessException("Unauthorized");
        }

        businessRepository.delete(business);
    }

    public List<BusinessResponseDTO> getUserBusinesses(UUID userId) {
        return businessRepository.findByOwnerId(userId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    private BusinessResponseDTO toDTO(Business business) {
        List<ServiceResponseDTO> businessServices = providedServicesService.getBusinessServices(business.getId());
        User owner = userService.getUserById(business.getOwner().getId());
        List<EmployeeResponseDTO> employeeList = employeeService.getBusinessEmployeesList(business.getId());
        Set<BusinessFeatureDTO> featureList = featureService.getAllFeatures(business.getId());
        List<LocationResponseDTO> locationList = locationService.getLocationsForBusiness(business.getId());

        return BusinessMapper.toDTO(business, businessServices, employeeList, featureList, locationList, owner, imageUrls);
    }

    private User getUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = (String) auth.getPrincipal();
        return getUserByUsername(username);
    }

    private User getUserByUsername(String username) {
        return userService.getUserByUsername(username);
    }

    public Page<BusinessResponseDTO> listBusinessesByQuery(String query, Pageable pageable) {
        return businessRepository.findByNameContainingIgnoreCase(query, pageable)
                .map(this::toDTO);
    }
}
