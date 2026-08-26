package com.platform.service;

import com.platform.dto.business.BusinessFeatureDTO;
import com.platform.dto.business.BusinessMapper;
import com.platform.dto.business.BusinessRequestDTO;
import com.platform.dto.business.BusinessResponseDTO;
import com.platform.dto.employee.EmployeeResponseDTO;
import com.platform.dto.location.LocationResponseDTO;
import com.platform.dto.service.ServiceResponseDTO;
import com.platform.entity.Business;
import com.platform.entity.User;
import com.platform.exception.BusinessException;
import com.platform.exception.ResourceNotFoundException;
import com.platform.repository.BusinessRepository;
import com.platform.utils.SlugGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

    private static final String BUSINESS_EXCEPTION = "Business not found";

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
                .logoUrl(dto.getLogoUrl())
                .coverImageUrl(dto.getCoverImageUrl())
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

    public Page<BusinessResponseDTO> listBusinesses(String city, Double minRating, String businessCategoryType, Pageable pageable) {
        List<Business> businesses;
        if (city != null && minRating != null) {
            businesses = businessRepository.findByFilters(city, minRating);
        } else if (city != null) {
            businesses = businessRepository.findByCity(city);
        } else if(businessCategoryType != null){
            businesses = businessRepository.findByBusinessCategory(businessCategoryType);
        } else {
            businesses = businessRepository.findAll();
        }

        return new PageImpl<>(
                businesses.stream().map(this::toDTO).toList(),
                pageable,
                businesses.size());
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
        business.setLogoUrl(dto.getLogoUrl());
        business.setCoverImageUrl(dto.getCoverImageUrl());

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

        return BusinessMapper.toDTO(business, businessServices, employeeList, featureList, locationList, owner);
    }

    private User getUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = (String) auth.getPrincipal();
        return getUserByUsername(username);
    }

    private User getUserByUsername(String username) {
        return userService.getUserByUsername(username);
    }

    public Page<BusinessResponseDTO> listBusinessesByQuery(String query, PageRequest pageable) {

        List<Business> businesses = businessRepository.findByNameContainingIgnoreCase(query);

        return new PageImpl<>(
                businesses.stream().map(this::toDTO).toList(),
                pageable,
                businesses.size());
    }
}
