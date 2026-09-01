package com.platform.service;

import com.platform.dto.service.ServiceRequestDTO;
import com.platform.dto.service.ServiceResponseDTO;
import com.platform.entity.Booking;
import com.platform.entity.Business;
import com.platform.entity.ProvidedService;
import com.platform.entity.User;
import com.platform.exception.ResourceNotFoundException;
import com.platform.exception.ServiceNotFoundException;
import com.platform.repository.BookingRepository;
import com.platform.repository.BusinessRepository;
import com.platform.repository.ServiceRepository;
import com.platform.repository.spec.ServiceSpecifications;
import com.platform.utils.BusinessOwnershipValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProvidedServicesService {

    private final ServiceRepository serviceRepository;
    private final BusinessRepository businessRepository;
    private final BookingRepository bookingRepository;

    private static final String SERVICE_EXCEPTION = "Service not found";

    /** Whitelisted {@code sort} values for the service list endpoints. */
    public static final Set<String> SORTABLE_FIELDS =
            Set.of("name", "price", "durationMinutes", "createdAt", "updatedAt");
    public static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "name");

    @Transactional
    public ServiceResponseDTO createService(UUID businessId, ServiceRequestDTO dto, User currentUser) {

        Business business = getBusinessById(businessId);

        BusinessOwnershipValidator.assertOwner(business, currentUser);

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

    /**
     * Services for many businesses in one query, grouped by business id. The list
     * endpoints call this once instead of {@link #getBusinessServices(UUID)} per
     * business. See BP-53.
     */
    public Map<UUID, List<ServiceResponseDTO>> getServicesByBusinessIds(Collection<UUID> businessIds) {
        if (businessIds.isEmpty()) return Map.of();
        return serviceRepository.findByBusinessIdIn(businessIds).stream()
                .collect(Collectors.groupingBy(
                        s -> s.getBusiness().getId(),
                        Collectors.mapping(this::toDTO, Collectors.toList())));
    }

    public List<ServiceResponseDTO> getActiveServices(UUID businessId) {
        return serviceRepository.findByBusinessIdAndActive(businessId, true)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * Paged, business-scoped list. With no {@code q} this hits the exact same query as
     * before (BP-12) - a blank/null search term is a no-op, not an "empty filter" that
     * happens to match everything. With a {@code q}, the search composes through
     * {@link ServiceSpecifications} so the business scope can never be left off; see the
     * class comment there.
     */
    public Page<ServiceResponseDTO> getBusinessServices(UUID businessId, String q, Pageable pageable) {
        if (isBlank(q)) {
            return serviceRepository.findByBusinessId(businessId, pageable)
                    .map(this::toDTO);
        }
        Specification<ProvidedService> spec = ServiceSpecifications.belongsToBusiness(businessId)
                .and(ServiceSpecifications.nameOrDescriptionContains(q));
        return serviceRepository.findAll(spec, pageable).map(this::toDTO);
    }

    /** Same as {@link #getBusinessServices(UUID, String, Pageable)}, scoped to active services only. */
    public Page<ServiceResponseDTO> getActiveServices(UUID businessId, String q, Pageable pageable) {
        if (isBlank(q)) {
            return serviceRepository.findByBusinessIdAndActive(businessId, true, pageable)
                    .map(this::toDTO);
        }
        Specification<ProvidedService> spec = ServiceSpecifications.belongsToBusiness(businessId)
                .and(ServiceSpecifications.isActive(true))
                .and(ServiceSpecifications.nameOrDescriptionContains(q));
        return serviceRepository.findAll(spec, pageable).map(this::toDTO);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Transactional
    public ServiceResponseDTO updateService(UUID businessId, UUID serviceId, ServiceRequestDTO dto, User currentUser) {

        Business business = getBusinessById(businessId);

        BusinessOwnershipValidator.assertOwner(business, currentUser);

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

        BusinessOwnershipValidator.assertOwner(business, currentUser);

        ProvidedService providedService = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException(SERVICE_EXCEPTION));

        List<Booking> serviceBookings = bookingRepository.findByProvidedServiceId(serviceId);
        bookingRepository.deleteAll(serviceBookings);

        serviceRepository.delete(providedService);
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
