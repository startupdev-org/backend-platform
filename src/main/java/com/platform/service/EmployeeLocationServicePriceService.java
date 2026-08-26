package com.platform.service;

import com.platform.dto.EmployeeLocationServicePriceRequestDTO;
import com.platform.dto.EmployeeLocationServicePriceResponseDTO;
import com.platform.entity.Business;
import com.platform.entity.Employee;
import com.platform.entity.EmployeeLocationServicePrice;
import com.platform.entity.Location;
import com.platform.entity.ProvidedService;
import com.platform.entity.User;
import com.platform.exception.BusinessException;
import com.platform.exception.ResourceNotFoundException;
import com.platform.repository.BusinessRepository;
import com.platform.repository.EmployeeLocationServicePriceRepository;
import com.platform.repository.EmployeeRepository;
import com.platform.repository.LocationRepository;
import com.platform.repository.ServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeLocationServicePriceService {

    private final EmployeeLocationServicePriceRepository priceRepository;
    private final EmployeeRepository employeeRepository;
    private final ServiceRepository serviceRepository;
    private final LocationRepository locationRepository;
    private final BusinessRepository businessRepository;

    private static final String BUSINESS_NOT_FOUND = "Business not found";
    private static final String PRICE_ENTRY_NOT_FOUND = "Price entry not found";

    @Transactional
    public EmployeeLocationServicePriceResponseDTO create(UUID businessId, EmployeeLocationServicePriceRequestDTO dto, User currentUser) {
        log.info("Creating price entry for businessId={}, employeeId={}, serviceId={}, locationId={}",
                businessId, dto.employeeId(), dto.serviceId(), dto.locationId());

        Business business = loadBusinessAndValidateOwnership(businessId, currentUser);

        if (priceRepository.existsByEmployeeIdAndServiceIdAndLocationId(
                dto.employeeId(), dto.serviceId(), dto.locationId())) {
            log.warn("Duplicate price entry attempted for employeeId={}, serviceId={}, locationId={}",
                    dto.employeeId(), dto.serviceId(), dto.locationId());
            throw new BusinessException("A price entry already exists for this employee/service/location combination");
        }

        Employee employee = employeeRepository.findById(dto.employeeId())
                .orElseThrow(() -> {
                    log.error("Employee not found: id={}", dto.employeeId());
                    return new ResourceNotFoundException("Employee not found");
                });

        ProvidedService service = serviceRepository.findById(dto.serviceId())
                .orElseThrow(() -> {
                    log.error("Service not found: id={}", dto.serviceId());
                    return new ResourceNotFoundException("Service not found");
                });

        Location location = locationRepository.findById(dto.locationId())
                .orElseThrow(() -> {
                    log.error("Location not found: id={}", dto.locationId());
                    return new ResourceNotFoundException("Location not found");
                });

        validateSameBusiness(business, employee, service, location);

        EmployeeLocationServicePrice entity = EmployeeLocationServicePrice.builder()
                .employee(employee)
                .service(service)
                .location(location)
                .price(dto.price())
                .build();

        entity = priceRepository.save(entity);
        log.info("Created price entry id={} with price={}", entity.getId(), entity.getPrice());
        return toDTO(entity);
    }

    @Transactional
    public EmployeeLocationServicePriceResponseDTO update(UUID businessId, UUID id, EmployeeLocationServicePriceRequestDTO dto, User currentUser) {
        log.info("Updating price entry id={} for businessId={}", id, businessId);

        Business business = loadBusinessAndValidateOwnership(businessId, currentUser);

        EmployeeLocationServicePrice entity = getPriceForBusiness(business, id);

        // If the combination changed, check for duplicate
        boolean combinationChanged =
                !entity.getEmployee().getId().equals(dto.employeeId()) ||
                !entity.getService().getId().equals(dto.serviceId()) ||
                !entity.getLocation().getId().equals(dto.locationId());

        if (combinationChanged && priceRepository.existsByEmployeeIdAndServiceIdAndLocationId(
                dto.employeeId(), dto.serviceId(), dto.locationId())) {
            log.warn("Update would create duplicate for employeeId={}, serviceId={}, locationId={}",
                    dto.employeeId(), dto.serviceId(), dto.locationId());
            throw new BusinessException("A price entry already exists for this employee/service/location combination");
        }

        if (combinationChanged) {
            Employee employee = employeeRepository.findById(dto.employeeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
            ProvidedService service = serviceRepository.findById(dto.serviceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Service not found"));
            Location location = locationRepository.findById(dto.locationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Location not found"));

            validateSameBusiness(business, employee, service, location);

            entity.setEmployee(employee);
            entity.setService(service);
            entity.setLocation(location);
        }

        entity.setPrice(dto.price());
        entity = priceRepository.save(entity);

        log.info("Updated price entry id={}, new price={}", entity.getId(), entity.getPrice());
        return toDTO(entity);
    }

    @Transactional(readOnly = true)
    public EmployeeLocationServicePriceResponseDTO getById(UUID businessId, UUID id) {
        log.debug("Fetching price entry id={} for businessId={}", id, businessId);
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException(BUSINESS_NOT_FOUND));
        return toDTO(getPriceForBusiness(business, id));
    }

    @Transactional(readOnly = true)
    public List<EmployeeLocationServicePriceResponseDTO> getByEmployee(UUID businessId, UUID employeeId) {
        log.debug("Fetching all price entries for businessId={}, employeeId={}", businessId, employeeId);
        validateEmployeeBelongsToBusiness(businessId, employeeId);
        return priceRepository.findByEmployeeId(employeeId)
                .stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public List<EmployeeLocationServicePriceResponseDTO> getByEmployeeAndLocation(
            UUID businessId, UUID employeeId, UUID locationId) {
        log.debug("Fetching price entries for businessId={}, employeeId={}, locationId={}", businessId, employeeId, locationId);
        validateEmployeeBelongsToBusiness(businessId, employeeId);
        return priceRepository.findByEmployeeIdAndLocationId(employeeId, locationId)
                .stream().map(this::toDTO).toList();
    }

    @Transactional
    public void delete(UUID businessId, UUID id, User currentUser) {
        log.info("Deleting price entry id={} for businessId={}", id, businessId);
        Business business = loadBusinessAndValidateOwnership(businessId, currentUser);
        EmployeeLocationServicePrice entity = getPriceForBusiness(business, id);
        priceRepository.delete(entity);
        log.info("Deleted price entry id={}", id);
    }

    // ── Ownership / consistency helpers ─────────────────────────────────────────

    private Business loadBusinessAndValidateOwnership(UUID businessId, User currentUser) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException(BUSINESS_NOT_FOUND));

        if (business.isNotOwner(currentUser) &&
                !currentUser.getRole().equals(User.UserRole.PLATFORM_ADMIN)) {
            throw new BusinessException("Unauthorized");
        }

        return business;
    }

    private void validateSameBusiness(Business business, Employee employee, ProvidedService service, Location location) {
        UUID businessId = business.getId();
        if (!employee.getBusiness().getId().equals(businessId) ||
                !service.getBusiness().getId().equals(businessId) ||
                !location.getBusiness().getId().equals(businessId)) {
            throw new BusinessException("Employee, service and location must all belong to the same business");
        }
    }

    private void validateEmployeeBelongsToBusiness(UUID businessId, UUID employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        if (!employee.getBusiness().getId().equals(businessId)) {
            throw new ResourceNotFoundException("Employee not found");
        }
    }

    private EmployeeLocationServicePrice getPriceForBusiness(Business business, UUID id) {
        EmployeeLocationServicePrice entity = priceRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Price entry not found: id={}", id);
                    return new ResourceNotFoundException(PRICE_ENTRY_NOT_FOUND);
                });

        if (!entity.getEmployee().getBusiness().getId().equals(business.getId())) {
            throw new ResourceNotFoundException(PRICE_ENTRY_NOT_FOUND);
        }

        return entity;
    }

    // ── Mapper ────────────────────────────────────────────────────────────────
    private EmployeeLocationServicePriceResponseDTO toDTO(EmployeeLocationServicePrice e) {
        return new EmployeeLocationServicePriceResponseDTO(
                e.getId(),
                e.getEmployee().getId(),
                e.getEmployee().getFirstName() + " " + e.getEmployee().getLastName(),
                e.getService().getId(),
                e.getService().getName(),
                e.getLocation().getId(),
                e.getLocation().getName(),
                e.getPrice()
        );
    }
}
