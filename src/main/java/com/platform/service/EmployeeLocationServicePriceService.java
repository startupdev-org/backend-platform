package com.platform.service;

import com.platform.dto.EmployeeLocationServicePriceRequestDTO;
import com.platform.dto.EmployeeLocationServicePriceResponseDTO;
import com.platform.dto.EmployeeServiceAssignmentRequestDTO;
import com.platform.dto.EmployeeServiceAssignmentResponseDTO;
import com.platform.entity.Business;
import com.platform.entity.Employee;
import com.platform.entity.EmployeeLocationServicePrice;
import com.platform.entity.Location;
import com.platform.entity.ProvidedService;
import com.platform.entity.User;
import com.platform.exception.BadRequestException;
import com.platform.exception.BusinessException;
import com.platform.exception.ResourceNotFoundException;
import com.platform.repository.BusinessRepository;
import com.platform.repository.EmployeeLocationServicePriceRepository;
import com.platform.repository.EmployeeRepository;
import com.platform.repository.LocationRepository;
import com.platform.repository.ServiceRepository;
import com.platform.utils.BusinessOwnershipValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private static final String EMPLOYEE_NOT_FOUND = "Employee not found";
    private static final String SERVICE_NOT_FOUND = "Service not found";

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
                    return new ResourceNotFoundException(EMPLOYEE_NOT_FOUND);
                });

        ProvidedService service = serviceRepository.findById(dto.serviceId())
                .orElseThrow(() -> {
                    log.error("Service not found: id={}", dto.serviceId());
                    return new ResourceNotFoundException(SERVICE_NOT_FOUND);
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

    /**
     * Makes an employee bookable for a set of services without hand-building the join rows.
     *
     * <p>Whether an employee can perform a service is not a flag - it is the existence of an
     * (employee, service, location) row, which is also where the price lives. Elegant, but it
     * means the simplest possible business cannot take a booking until someone builds that row
     * by hand ({@code BookingService.createBooking} hard-fails without it). This is the
     * convenience path over that model: one call, base price, no pricing decisions. See BP-50.
     *
     * <p><b>Locations.</b> An {@link Employee} has no location of its own - it belongs to a
     * business, nothing narrower - so "across their locations" can only mean every location of
     * the business, and a row is created per location. A business with no locations at all is a
     * real precondition failure, not a silent no-op: the caller gets a 400 telling them to add a
     * location first.
     *
     * <p><b>Idempotency.</b> Re-posting the same service ids is a success, not a 409. Rows that
     * already exist are left exactly as they are - so a per-employee override is never reset to
     * base price by a re-assign - and only the missing ones are inserted. The existing rows are
     * read in one query rather than one per pair.
     *
     * <p><b>Under concurrency</b> the unique constraint stays the final authority: two calls that
     * race past the pre-check both insert, one gets a {@code DataIntegrityViolationException}
     * (→ 409, mapped globally) and its whole transaction rolls back. That is safe to retry - the
     * retry sees the winner's rows and returns 200 with {@code createdCount} at zero. The
     * pre-check is what makes the ordinary repeat call cheap and quiet, not what makes the
     * constraint redundant.
     *
     * <p>Prices are never taken from the request body here; the explicit override endpoints
     * ({@link #create} / {@link #update}) remain the only way to set a non-base price.
     */
    @Transactional
    public EmployeeServiceAssignmentResponseDTO assignServicesAtBasePrice(
            UUID businessId, UUID employeeId, EmployeeServiceAssignmentRequestDTO dto, User currentUser) {

        log.info("Assigning {} service(s) at base price to employeeId={} of businessId={}",
                dto.serviceIds().size(), employeeId, businessId);

        Business business = loadBusinessAndValidateOwnership(businessId, currentUser);

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> {
                    log.error("Employee not found: id={}", employeeId);
                    return new ResourceNotFoundException(EMPLOYEE_NOT_FOUND);
                });

        List<Location> locations = locationRepository.findByBusinessId(businessId);
        if (locations.isEmpty()) {
            throw new BadRequestException(
                    "This business has no locations yet. Create at least one location before "
                            + "assigning services to an employee.");
        }

        // Distinct, order-preserving: a repeated id in the body must not try to insert twice.
        List<UUID> serviceIds = dto.serviceIds().stream().distinct().toList();

        // Resolve and validate everything before writing anything, so a service belonging to
        // another tenant is rejected outright rather than half-applied.
        List<ProvidedService> services = serviceIds.stream()
                .map(serviceId -> serviceRepository.findById(serviceId)
                        .orElseThrow(() -> {
                            log.error("Service not found: id={}", serviceId);
                            return new ResourceNotFoundException(SERVICE_NOT_FOUND);
                        }))
                .toList();

        for (ProvidedService service : services) {
            for (Location location : locations) {
                validateSameBusiness(business, employee, service, location);
            }
        }

        // One query for what already exists, keyed by (service, location) - not one per pair.
        Map<List<UUID>, EmployeeLocationServicePrice> existing = priceRepository.findByEmployeeId(employeeId)
                .stream()
                .collect(Collectors.toMap(
                        p -> List.of(p.getService().getId(), p.getLocation().getId()),
                        p -> p,
                        (a, b) -> a));

        List<EmployeeLocationServicePrice> toCreate = new ArrayList<>();
        List<EmployeeLocationServicePrice> alreadyAssigned = new ArrayList<>();

        for (ProvidedService service : services) {
            for (Location location : locations) {
                EmployeeLocationServicePrice current = existing.get(List.of(service.getId(), location.getId()));
                if (current != null) {
                    // Left untouched on purpose: a per-employee override must survive a re-assign.
                    alreadyAssigned.add(current);
                    continue;
                }
                toCreate.add(EmployeeLocationServicePrice.builder()
                        .employee(employee)
                        .service(service)
                        .location(location)
                        .price(service.getPrice())
                        .build());
            }
        }

        List<EmployeeLocationServicePrice> created = toCreate.isEmpty()
                ? List.of()
                : priceRepository.saveAll(toCreate);

        log.info("Assigned services to employeeId={}: {} row(s) created, {} already present",
                employeeId, created.size(), alreadyAssigned.size());

        List<EmployeeLocationServicePriceResponseDTO> assignments =
                Stream.concat(created.stream(), alreadyAssigned.stream())
                        .map(this::toDTO)
                        .toList();

        return new EmployeeServiceAssignmentResponseDTO(
                employeeId, created.size(), alreadyAssigned.size(), assignments);
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
                    .orElseThrow(() -> new ResourceNotFoundException(EMPLOYEE_NOT_FOUND));
            ProvidedService service = serviceRepository.findById(dto.serviceId())
                    .orElseThrow(() -> new ResourceNotFoundException(SERVICE_NOT_FOUND));
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

        BusinessOwnershipValidator.assertOwner(business, currentUser);

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
                .orElseThrow(() -> new ResourceNotFoundException(EMPLOYEE_NOT_FOUND));
        if (!employee.getBusiness().getId().equals(businessId)) {
            throw new ResourceNotFoundException(EMPLOYEE_NOT_FOUND);
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
