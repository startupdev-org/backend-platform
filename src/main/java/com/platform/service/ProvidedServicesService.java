package com.platform.service;

import com.platform.dto.service.ServiceRequestDTO;
import com.platform.dto.service.ServiceResponseDTO;
import com.platform.entity.Booking;
import com.platform.entity.Business;
import com.platform.entity.ProvidedService;
import com.platform.entity.User;
import com.platform.exception.BusinessException;
import com.platform.exception.ResourceNotFoundException;
import com.platform.exception.ServiceNotFoundException;
import com.platform.repository.BookingRepository;
import com.platform.repository.BusinessRepository;
import com.platform.repository.ServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProvidedServicesService {

    private final ServiceRepository serviceRepository;
    private final UserService userService;
    private final BusinessRepository businessRepository;
    private final BookingRepository bookingRepository;

    private static final String SERVICE_EXCEPTION = "Service not found";

    @Transactional
    public ServiceResponseDTO createService(UUID businessId, ServiceRequestDTO dto) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        Business business = getBusinessById(businessId);

        User user = userService.getUserByUsername(authentication.getName());

        validateBusinessOwnership(business, user);

        ProvidedService providedService = ProvidedService.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .price(dto.getPrice())
                .durationMinutes(dto.getDurationMinutes())
                .active(true)
                .business(business)
                .build();

        providedService = serviceRepository.save(providedService);
        return toDTO(providedService);
    }

    public ServiceResponseDTO getService(UUID id) {
        ProvidedService providedService = serviceRepository.findById(id)
                .orElseThrow(() -> new ServiceNotFoundException(SERVICE_EXCEPTION));
        return toDTO(providedService);
    }

    public List<ServiceResponseDTO> getBusinessServices(UUID businessId) {
        return serviceRepository.findByBusinessId(businessId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    public List<ServiceResponseDTO> getActiveServices(UUID businessId) {
        return serviceRepository.findByBusinessIdAndActive(businessId, true)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    public Page<ServiceResponseDTO> getBusinessServices(UUID businessId, Pageable pageable) {
        List<ProvidedService> services = serviceRepository.findByBusinessId(businessId);
        return new PageImpl<>(
                services.stream().map(this::toDTO).toList(),
                pageable,
                services.size());
    }

    public Page<ServiceResponseDTO> getActiveServices(UUID businessId, Pageable pageable) {
        List<ProvidedService> services = serviceRepository.findByBusinessIdAndActive(businessId, true);
        return new PageImpl<>(
                services.stream().map(this::toDTO).toList(),
                pageable,
                services.size());
    }

    @Transactional
    public ServiceResponseDTO updateService(UUID businessId, UUID serviceId, ServiceRequestDTO dto) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        Business business = getBusinessById(businessId);

        User user = userService.getUserByUsername(authentication.getName());

        validateBusinessOwnership(business, user);


        ProvidedService providedService = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException(SERVICE_EXCEPTION));

        providedService.setName(dto.getName());
        providedService.setDescription(dto.getDescription());
        providedService.setPrice(dto.getPrice());
        providedService.setDurationMinutes(dto.getDurationMinutes());
        if (dto.getActive() != null) {
            providedService.setActive(dto.getActive());
        }

        providedService = serviceRepository.save(providedService);
        return toDTO(providedService);
    }

    @Transactional
    public void deleteService(UUID businessId, UUID serviceId, User currentUser) {

        Business business = getBusinessById(businessId);

        validateBusinessOwnership(business, currentUser);

        ProvidedService providedService = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException(SERVICE_EXCEPTION));

        List<Booking> serviceBookings = bookingRepository.findByProvidedServiceId(serviceId);
        bookingRepository.deleteAll(serviceBookings);

        serviceRepository.delete(providedService);
    }

    private void validateBusinessOwnership(Business business, User currentUser) {
        if (!business.getOwner().getId().equals(currentUser.getId()) &&
            !currentUser.getRole().equals(User.UserRole.PLATFORM_ADMIN)) {
            throw new BusinessException("Unauthorized");
        }
    }

    private Business getBusinessById(UUID businessId) {
        return businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));
    }

    private ServiceResponseDTO toDTO(ProvidedService providedService) {
        return ServiceResponseDTO.builder()
                .id(providedService.getId())
                .name(providedService.getName())
                .description(providedService.getDescription())
                .price(providedService.getPrice())
                .durationMinutes(providedService.getDurationMinutes())
                .businessId(providedService.getBusiness().getId())
                .active(providedService.getActive())
                .createdAt(providedService.getCreatedAt())
                .updatedAt(providedService.getUpdatedAt())
                .build();
    }
}
