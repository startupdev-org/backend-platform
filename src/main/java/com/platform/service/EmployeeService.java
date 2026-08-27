package com.platform.service;

import com.platform.dto.employee.EmployeeMapper;
import com.platform.dto.employee.EmployeeRequestDTO;
import com.platform.dto.employee.EmployeeResponseDTO;
import com.platform.entity.Business;
import com.platform.entity.Employee;
import com.platform.entity.User;
import com.platform.exception.BusinessException;
import com.platform.exception.ResourceNotFoundException;
import com.platform.repository.BookingRepository;
import com.platform.repository.BusinessRepository;
import com.platform.repository.EmployeeRepository;
import com.platform.storage.ImageUrlResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final BusinessRepository businessRepository;
    private final BookingRepository bookingRepository;
    private final UserService userService;
    private final ImageUrlResolver imageUrls;

    private static final String BUSINESS_EXCEPTION = "Business not found";
    private static final String EMPLOYEE_NOT_FOUND_EXCEPTION = "Employee not found";

    /** Whitelisted {@code sort} values for the employee list endpoints. */
    public static final Set<String> SORTABLE_FIELDS =
            Set.of("firstName", "lastName", "email", "createdAt", "updatedAt");
    public static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "createdAt");

    @Transactional
    public EmployeeResponseDTO createEmployee(UUID businessId, EmployeeRequestDTO dto) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException(BUSINESS_EXCEPTION));

        User currentUser = getUser();

        validateBusinessOwnership(business, currentUser);

        Employee employee = Employee.builder()
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .email(dto.getEmail())
                .phoneNumber(dto.getPhoneNumber())
                .enabled(true)
                .business(business)
                .build();

        employee = employeeRepository.save(employee);
        return toDTO(employee);
    }

    public EmployeeResponseDTO getEmployee(UUID id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(EMPLOYEE_NOT_FOUND_EXCEPTION));
        if (!Boolean.TRUE.equals(employee.getEnabled())) {
            throw new ResourceNotFoundException(EMPLOYEE_NOT_FOUND_EXCEPTION);
        }
        return toDTO(employee);
    }

    public EmployeeResponseDTO getEmployeeForAdmin(UUID employeeId, User currentUser) {
        validatePlatformAdmin(currentUser);

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException(EMPLOYEE_NOT_FOUND_EXCEPTION));
        return toDTO(employee);
    }

    public List<EmployeeResponseDTO> getBusinessEmployeesList(UUID businessId) {
        List<Employee> employees;

        employees = employeeRepository.findByBusinessIdAndEnabled(businessId, true)
                .stream()
                .toList();

        return employees.stream().map(this::toDTO).toList();
    }

    public Page<EmployeeResponseDTO> getBusinessEmployees(UUID businessId, Pageable pageable) {
        return employeeRepository.findByBusinessIdAndEnabled(businessId, true, pageable)
                .map(this::toDTO);
    }

    public Page<EmployeeResponseDTO> getActiveEmployees(UUID businessId, Pageable pageable) {
        return employeeRepository.findByBusinessIdAndEnabled(businessId, true, pageable)
                .map(this::toDTO);
    }

    @Transactional
    public EmployeeResponseDTO updateEmployee(UUID businessId, UUID employeeId, EmployeeRequestDTO dto, User currentUser) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException(BUSINESS_EXCEPTION));

        validateBusinessOwnership(business, currentUser);

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException(EMPLOYEE_NOT_FOUND_EXCEPTION));

        validateEmployeeVisibleToCaller(employee, currentUser);

        employee.setFirstName(dto.getFirstName());
        employee.setLastName(dto.getLastName());
        if (dto.getEmail() != null) {
            employee.setEmail(dto.getEmail());
        }
        if (dto.getPhoneNumber() != null) {
            employee.setPhoneNumber(dto.getPhoneNumber());
        }
        if (dto.getEnabled() != null) {
            employee.setEnabled(dto.getEnabled());
        }

        employee = employeeRepository.save(employee);
        return toDTO(employee);
    }

    @Transactional
    public void deleteEmployee(UUID businessId, UUID employeeId, User currentUser) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException(BUSINESS_EXCEPTION));

        validateBusinessOwnership(business, currentUser);

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException(EMPLOYEE_NOT_FOUND_EXCEPTION));

        validateEmployeeVisibleToCaller(employee, currentUser);

        employee.setEnabled(false);
        employeeRepository.save(employee);
    }

    @Transactional
    public void hardDeleteEmployee(UUID businessId, UUID employeeId, User currentUser) {
        validatePlatformAdmin(currentUser);

        businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException(BUSINESS_EXCEPTION));

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException(EMPLOYEE_NOT_FOUND_EXCEPTION));

        List<com.platform.entity.Booking> employeeBookings = bookingRepository.findByEmployeeId(employeeId);
        bookingRepository.deleteAll(employeeBookings);

        employeeRepository.delete(employee);
    }

    private void validateBusinessOwnership(Business business, User currentUser) {
        if (!business.getOwner().getId().equals(currentUser.getId()) &&
            !currentUser.getRole().equals(User.UserRole.PLATFORM_ADMIN)) {
            throw new BusinessException("Unauthorized");
        }
    }

    private void validateEmployeeVisibleToCaller(Employee employee, User currentUser) {
        if (!Boolean.TRUE.equals(employee.getEnabled()) &&
            !currentUser.getRole().equals(User.UserRole.PLATFORM_ADMIN)) {
            throw new ResourceNotFoundException(EMPLOYEE_NOT_FOUND_EXCEPTION);
        }
    }

    private void validatePlatformAdmin(User currentUser) {
        if (!currentUser.getRole().equals(User.UserRole.PLATFORM_ADMIN)) {
            throw new BusinessException("Unauthorized");
        }
    }

    private EmployeeResponseDTO toDTO(Employee employee) {
        return EmployeeMapper.toDTO(employee, imageUrls);
    }

    private User getUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = (String) auth.getPrincipal();
        return getUserByUsername(username);
    }

    private User getUserByUsername(String username) {
        return userService.getUserByUsername(username);
    }
}
