package com.platform.service;

import com.platform.dto.EmployeeLocationServicePriceRequestDTO;
import com.platform.dto.EmployeeLocationServicePriceResponseDTO;
import com.platform.entity.Employee;
import com.platform.entity.EmployeeLocationServicePrice;
import com.platform.entity.Location;
import com.platform.entity.ProvidedService;
import com.platform.exception.BusinessException;
import com.platform.exception.ResourceNotFoundException;
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

    @Transactional
    public EmployeeLocationServicePriceResponseDTO create(EmployeeLocationServicePriceRequestDTO dto) {
        log.info("Creating price entry for employeeId={}, serviceId={}, locationId={}",
                dto.employeeId(), dto.serviceId(), dto.locationId());

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
    public EmployeeLocationServicePriceResponseDTO update(UUID id, EmployeeLocationServicePriceRequestDTO dto) {
        log.info("Updating price entry id={}", id);

        EmployeeLocationServicePrice entity = priceRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Price entry not found: id={}", id);
                    return new ResourceNotFoundException("Price entry not found");
                });

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
    public EmployeeLocationServicePriceResponseDTO getById(UUID id) {
        log.debug("Fetching price entry id={}", id);
        return priceRepository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> {
                    log.error("Price entry not found: id={}", id);
                    return new ResourceNotFoundException("Price entry not found");
                });
    }

    @Transactional(readOnly = true)
    public List<EmployeeLocationServicePriceResponseDTO> getByEmployee(UUID employeeId) {
        log.debug("Fetching all price entries for employeeId={}", employeeId);
        return priceRepository.findByEmployeeId(employeeId)
                .stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public List<EmployeeLocationServicePriceResponseDTO> getByEmployeeAndLocation(
            UUID employeeId, UUID locationId) {
        log.debug("Fetching price entries for employeeId={}, locationId={}", employeeId, locationId);
        return priceRepository.findByEmployeeIdAndLocationId(employeeId, locationId)
                .stream().map(this::toDTO).toList();
    }

    @Transactional
    public void delete(UUID id) {
        log.info("Deleting price entry id={}", id);
        EmployeeLocationServicePrice entity = priceRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Price entry not found for deletion: id={}", id);
                    return new ResourceNotFoundException("Price entry not found");
                });
        priceRepository.delete(entity);
        log.info("Deleted price entry id={}", id);
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