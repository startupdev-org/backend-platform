package com.platform.service;

import com.platform.dto.business.BusinessResponseDTO;
import com.platform.dto.employee.EmployeeMapper;
import com.platform.dto.employee.EmployeeResponseDTO;
import com.platform.dto.image.UploadUrlResponseDTO;
import com.platform.entity.Business;
import com.platform.entity.Employee;
import com.platform.entity.User;
import com.platform.exception.BadRequestException;
import com.platform.exception.BusinessException;
import com.platform.exception.ResourceNotFoundException;
import com.platform.exception.StorageException;
import com.platform.repository.BusinessRepository;
import com.platform.repository.EmployeeRepository;
import com.platform.storage.ImageKeys;
import com.platform.storage.ImageTarget;
import com.platform.storage.StorageProperties;
import com.platform.storage.StorageProvider;
import com.platform.storage.StoredObject;
import com.platform.storage.UploadTarget;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Image upload orchestration for business logos, covers, and employee photos.
 *
 * <p>Three steps, and the third is the one that carries the security weight:
 * <ol>
 *   <li>{@code createUploadUrl*} - ownership check, then a server-generated key and a
 *       short-lived presigned URL.</li>
 *   <li>The browser PUTs the bytes straight to the bucket. Nothing here sees them.</li>
 *   <li>{@code attach*} - ownership check, <b>key-prefix check</b>, existence and size
 *       check, then persist. The prefix check is what stops a caller from attaching
 *       another tenant's object, or an arbitrary string, to their own row.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageService {

    private final BusinessRepository businessRepository;
    private final EmployeeRepository employeeRepository;
    private final BusinessService businessService;
    private final StorageProvider storageProvider;
    private final StorageProperties storageProperties;

    private static final String BUSINESS_NOT_FOUND = "Business not found";
    private static final String EMPLOYEE_NOT_FOUND = "Employee not found";

    // ── Business logo / cover ─────────────────────────────────────────────────

    public UploadUrlResponseDTO createBusinessUploadUrl(
            UUID businessId, ImageTarget target, String contentType, User currentUser) {

        requireBusinessTarget(target);
        Business business = requireOwnedBusiness(businessId, currentUser);

        String key = ImageKeys.generate(
                ImageKeys.businessPrefix(business.getId(), target), contentType);

        return toResponse(storageProvider.createUploadTarget(key, contentType));
    }

    @Transactional
    public BusinessResponseDTO attachBusinessImage(
            UUID businessId, ImageTarget target, String storageKey, User currentUser) {

        requireBusinessTarget(target);
        Business business = requireOwnedBusiness(businessId, currentUser);

        ImageKeys.requirePrefix(storageKey, ImageKeys.businessPrefix(business.getId(), target));
        verifyUploaded(storageKey);

        String previousKey = currentBusinessKey(business, target);
        applyBusinessKey(business, target, storageKey);
        businessRepository.save(business);

        deleteQuietly(previousKey, storageKey);
        return businessService.getBusinessDTOById(businessId);
    }

    @Transactional
    public BusinessResponseDTO clearBusinessImage(UUID businessId, ImageTarget target, User currentUser) {
        requireBusinessTarget(target);
        Business business = requireOwnedBusiness(businessId, currentUser);

        String previousKey = currentBusinessKey(business, target);
        applyBusinessKey(business, target, null);
        businessRepository.save(business);

        deleteQuietly(previousKey, null);
        return businessService.getBusinessDTOById(businessId);
    }

    // ── Employee photo ────────────────────────────────────────────────────────

    public UploadUrlResponseDTO createEmployeePhotoUploadUrl(
            UUID businessId, UUID employeeId, String contentType, User currentUser) {

        Employee employee = requireOwnedEmployee(businessId, employeeId, currentUser);

        String key = ImageKeys.generate(
                ImageKeys.employeePhotoPrefix(businessId, employee.getId()), contentType);

        return toResponse(storageProvider.createUploadTarget(key, contentType));
    }

    @Transactional
    public EmployeeResponseDTO attachEmployeePhoto(
            UUID businessId, UUID employeeId, String storageKey, User currentUser) {

        Employee employee = requireOwnedEmployee(businessId, employeeId, currentUser);

        ImageKeys.requirePrefix(storageKey, ImageKeys.employeePhotoPrefix(businessId, employee.getId()));
        verifyUploaded(storageKey);

        String previousKey = employee.getPhotoKey();
        employee.setPhotoKey(storageKey);
        employee = employeeRepository.save(employee);

        deleteQuietly(previousKey, storageKey);
        return EmployeeMapper.toDTO(employee, storageProvider);
    }

    @Transactional
    public EmployeeResponseDTO clearEmployeePhoto(UUID businessId, UUID employeeId, User currentUser) {
        Employee employee = requireOwnedEmployee(businessId, employeeId, currentUser);

        String previousKey = employee.getPhotoKey();
        employee.setPhotoKey(null);
        employee = employeeRepository.save(employee);

        deleteQuietly(previousKey, null);
        return EmployeeMapper.toDTO(employee, storageProvider);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Business requireOwnedBusiness(UUID businessId, User currentUser) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException(BUSINESS_NOT_FOUND));
        validateBusinessOwnership(business, currentUser);
        return business;
    }

    private Employee requireOwnedEmployee(UUID businessId, UUID employeeId, User currentUser) {
        Business business = requireOwnedBusiness(businessId, currentUser);

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException(EMPLOYEE_NOT_FOUND));

        // Scope the employee to the business in the path, so a valid employee id from
        // another tenant reads as "not found" rather than resolving.
        if (!employee.getBusiness().getId().equals(business.getId())) {
            throw new ResourceNotFoundException(EMPLOYEE_NOT_FOUND);
        }
        return employee;
    }

    private void validateBusinessOwnership(Business business, User currentUser) {
        if (business.isNotOwner(currentUser) &&
                !currentUser.getRole().equals(User.UserRole.PLATFORM_ADMIN)) {
            throw new BusinessException("Unauthorized");
        }
    }

    private void requireBusinessTarget(ImageTarget target) {
        if (target != ImageTarget.LOGO && target != ImageTarget.COVER) {
            throw new BadRequestException("Target must be LOGO or COVER");
        }
    }

    /**
     * Backstop for the size and type limits. The presigned upload bypasses this
     * application entirely, so this is the first moment we can look at what actually
     * landed - and the last moment we can reject it before it goes on a public page.
     */
    private void verifyUploaded(String storageKey) {
        Optional<StoredObject> stored = storageProvider.head(storageKey);

        if (stored.isEmpty()) {
            throw new BadRequestException("No uploaded file found for that storage key");
        }

        StoredObject object = stored.get();

        if (object.sizeBytes() > storageProperties.getMaxUploadBytes()) {
            deleteQuietly(storageKey, null);
            throw new BadRequestException(
                    "Image exceeds the maximum size of " + storageProperties.getMaxUploadBytes() + " bytes");
        }

        if (object.contentType() != null && !ImageKeys.isAllowedContentType(object.contentType())) {
            deleteQuietly(storageKey, null);
            throw new BadRequestException("Uploaded file is not a supported image type");
        }
    }

    /**
     * Best-effort cleanup of the object being replaced. A storage failure here must not
     * fail the user's request - the row is already correct, and the worst case is one
     * orphaned object.
     */
    private void deleteQuietly(String key, String keyBeingKept) {
        if (key == null || key.isBlank() || key.equals(keyBeingKept)) {
            return;
        }
        // Legacy rows hold a full URL rather than a key; there is no object of ours to remove.
        if (key.startsWith("http://") || key.startsWith("https://")) {
            return;
        }
        try {
            storageProvider.delete(key);
        } catch (StorageException e) {
            log.warn("Could not delete replaced image {}: {}", key, e.getMessage());
        }
    }

    private String currentBusinessKey(Business business, ImageTarget target) {
        return target == ImageTarget.LOGO ? business.getLogoKey() : business.getCoverImageKey();
    }

    private void applyBusinessKey(Business business, ImageTarget target, String key) {
        if (target == ImageTarget.LOGO) {
            business.setLogoKey(key);
        } else {
            business.setCoverImageKey(key);
        }
    }

    private UploadUrlResponseDTO toResponse(UploadTarget target) {
        return UploadUrlResponseDTO.builder()
                .uploadUrl(target.uploadUrl())
                .storageKey(target.storageKey())
                .expiresInSeconds(target.expiresInSeconds())
                .maxBytes(target.maxBytes())
                .build();
    }
}
