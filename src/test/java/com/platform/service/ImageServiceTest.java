package com.platform.service;

import com.platform.dto.business.BusinessResponseDTO;
import com.platform.dto.employee.EmployeeResponseDTO;
import com.platform.dto.image.UploadUrlResponseDTO;
import com.platform.entity.Business;
import com.platform.entity.Employee;
import com.platform.entity.User;
import com.platform.exception.BadRequestException;
import com.platform.exception.BusinessException;
import com.platform.exception.ResourceNotFoundException;
import com.platform.repository.BusinessRepository;
import com.platform.repository.EmployeeRepository;
import com.platform.storage.ImageKeys;
import com.platform.storage.ImageTarget;
import com.platform.storage.StorageProperties;
import com.platform.storage.StorageProvider;
import com.platform.storage.StoredObject;
import com.platform.storage.UploadTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImageServiceTest {

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private BusinessService businessService;

    @Mock
    private StorageProvider storageProvider;

    @Mock
    private StorageProperties storageProperties;

    @InjectMocks
    private ImageService imageService;

    private User owner;
    private User otherUser;
    private Business business;

    @BeforeEach
    void setUp() {
        owner = User.builder()
                .id(UUID.randomUUID())
                .email("owner@example.com")
                .role(User.UserRole.BUSINESS_ADMIN)
                .build();

        otherUser = User.builder()
                .id(UUID.randomUUID())
                .email("intruder@example.com")
                .role(User.UserRole.BUSINESS_ADMIN)
                .build();

        business = Business.builder()
                .id(UUID.randomUUID())
                .name("Test Business")
                .owner(owner)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ── Upload URL ────────────────────────────────────────────────────────────

    @Test
    void createBusinessUploadUrl_generatesKeyUnderTheBusinessPrefix() {
        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));
        when(storageProvider.createUploadTarget(anyString(), eq("image/png")))
                .thenAnswer(inv -> new UploadTarget("https://bucket/signed", inv.getArgument(0), 60, 5L));

        UploadUrlResponseDTO response = imageService.createBusinessUploadUrl(
                business.getId(), ImageTarget.LOGO, "image/png", owner);

        assertTrue(response.getStorageKey()
                .startsWith(ImageKeys.businessPrefix(business.getId(), ImageTarget.LOGO)));
        assertTrue(response.getStorageKey().endsWith(".png"));
        assertEquals("https://bucket/signed", response.getUploadUrl());
    }

    @Test
    void createBusinessUploadUrl_rejectsNonOwner() {
        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));

        assertThrows(BusinessException.class, () -> imageService.createBusinessUploadUrl(
                business.getId(), ImageTarget.LOGO, "image/png", otherUser));

        verify(storageProvider, never()).createUploadTarget(anyString(), anyString());
    }

    @Test
    void createBusinessUploadUrl_rejectsUnsupportedContentType() {
        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));

        assertThrows(BadRequestException.class, () -> imageService.createBusinessUploadUrl(
                business.getId(), ImageTarget.LOGO, "application/pdf", owner));
    }

    @Test
    void createBusinessUploadUrl_businessNotFound() {
        UUID missingId = UUID.randomUUID();
        when(businessRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> imageService.createBusinessUploadUrl(
                missingId, ImageTarget.LOGO, "image/png", owner));
    }

    // ── Attach ────────────────────────────────────────────────────────────────

    @Test
    void attachBusinessImage_persistsKeyAndDeletesThePreviousOne() {
        String previousKey = ImageKeys.businessPrefix(business.getId(), ImageTarget.LOGO) + "old.png";
        String newKey = ImageKeys.businessPrefix(business.getId(), ImageTarget.LOGO) + "new.png";
        business.setLogoKey(previousKey);

        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));
        when(storageProvider.head(newKey)).thenReturn(Optional.of(new StoredObject(1024, "image/png")));
        when(storageProperties.getMaxUploadBytes()).thenReturn(5L * 1024 * 1024);
        when(businessService.getBusinessDTOById(business.getId()))
                .thenReturn(BusinessResponseDTO.builder().id(business.getId()).build());

        imageService.attachBusinessImage(business.getId(), ImageTarget.LOGO, newKey, owner);

        assertEquals(newKey, business.getLogoKey());
        verify(businessRepository).save(business);
        verify(storageProvider, times(1)).delete(previousKey);
        verify(storageProvider, never()).delete(newKey);
    }

    @Test
    void attachBusinessImage_rejectsKeyBelongingToAnotherBusiness() {
        UUID otherBusinessId = UUID.randomUUID();
        String foreignKey = ImageKeys.businessPrefix(otherBusinessId, ImageTarget.LOGO) + "stolen.png";

        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));

        assertThrows(BadRequestException.class, () -> imageService.attachBusinessImage(
                business.getId(), ImageTarget.LOGO, foreignKey, owner));

        assertNull(business.getLogoKey());
        verify(businessRepository, never()).save(any());
        verify(storageProvider, never()).head(anyString());
    }

    @Test
    void attachBusinessImage_rejectsKeyForTheWrongSlot() {
        // A cover key must not be attachable as the logo, even within the same business.
        String coverKey = ImageKeys.businessPrefix(business.getId(), ImageTarget.COVER) + "c.png";
        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));

        assertThrows(BadRequestException.class, () -> imageService.attachBusinessImage(
                business.getId(), ImageTarget.LOGO, coverKey, owner));
    }

    @Test
    void attachBusinessImage_rejectsWhenNothingWasUploaded() {
        String key = ImageKeys.businessPrefix(business.getId(), ImageTarget.LOGO) + "ghost.png";
        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));
        when(storageProvider.head(key)).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class, () -> imageService.attachBusinessImage(
                business.getId(), ImageTarget.LOGO, key, owner));

        verify(businessRepository, never()).save(any());
    }

    @Test
    void attachBusinessImage_rejectsAndRemovesAnOversizedUpload() {
        String key = ImageKeys.businessPrefix(business.getId(), ImageTarget.LOGO) + "huge.png";
        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));
        when(storageProvider.head(key)).thenReturn(Optional.of(new StoredObject(99_000_000L, "image/png")));
        when(storageProperties.getMaxUploadBytes()).thenReturn(5L * 1024 * 1024);

        assertThrows(BadRequestException.class, () -> imageService.attachBusinessImage(
                business.getId(), ImageTarget.LOGO, key, owner));

        verify(storageProvider).delete(key);
        verify(businessRepository, never()).save(any());
    }

    @Test
    void attachBusinessImage_rejectsNonOwner() {
        String key = ImageKeys.businessPrefix(business.getId(), ImageTarget.LOGO) + "x.png";
        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));

        assertThrows(BusinessException.class, () -> imageService.attachBusinessImage(
                business.getId(), ImageTarget.LOGO, key, otherUser));

        verify(businessRepository, never()).save(any());
    }

    @Test
    void clearBusinessImage_removesTheObjectAndTheKey() {
        String key = ImageKeys.businessPrefix(business.getId(), ImageTarget.LOGO) + "old.png";
        business.setLogoKey(key);

        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));
        when(businessService.getBusinessDTOById(business.getId()))
                .thenReturn(BusinessResponseDTO.builder().id(business.getId()).build());

        imageService.clearBusinessImage(business.getId(), ImageTarget.LOGO, owner);

        assertNull(business.getLogoKey());
        verify(storageProvider).delete(key);
    }

    @Test
    void clearBusinessImage_leavesLegacyUrlRowsAlone() {
        // Pre-migration rows hold a full URL; there is no object of ours behind it.
        business.setLogoKey("https://old-host.example.com/logo.png");

        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));
        when(businessService.getBusinessDTOById(business.getId()))
                .thenReturn(BusinessResponseDTO.builder().id(business.getId()).build());

        imageService.clearBusinessImage(business.getId(), ImageTarget.LOGO, owner);

        assertNull(business.getLogoKey());
        verify(storageProvider, never()).delete(anyString());
    }

    // ── Employee photos ───────────────────────────────────────────────────────

    @Test
    void attachEmployeePhoto_persistsKey() {
        Employee employee = employee(business);
        String key = ImageKeys.employeePhotoPrefix(business.getId(), employee.getId()) + "p.webp";

        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));
        when(employeeRepository.findById(employee.getId())).thenReturn(Optional.of(employee));
        when(storageProvider.head(key)).thenReturn(Optional.of(new StoredObject(2048, "image/webp")));
        when(storageProperties.getMaxUploadBytes()).thenReturn(5L * 1024 * 1024);
        when(employeeRepository.save(employee)).thenReturn(employee);
        when(storageProvider.toPublicUrl(key)).thenReturn("https://bucket/public/" + key);

        EmployeeResponseDTO response = imageService.attachEmployeePhoto(
                business.getId(), employee.getId(), key, owner);

        assertEquals(key, employee.getPhotoKey());
        assertEquals("https://bucket/public/" + key, response.getPhotoUrl());
    }

    @Test
    void attachEmployeePhoto_rejectsAnEmployeeFromAnotherBusiness() {
        Business otherBusiness = Business.builder().id(UUID.randomUUID()).owner(otherUser).build();
        Employee foreignEmployee = employee(otherBusiness);
        String key = ImageKeys.employeePhotoPrefix(business.getId(), foreignEmployee.getId()) + "p.webp";

        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));
        when(employeeRepository.findById(foreignEmployee.getId())).thenReturn(Optional.of(foreignEmployee));

        assertThrows(ResourceNotFoundException.class, () -> imageService.attachEmployeePhoto(
                business.getId(), foreignEmployee.getId(), key, owner));

        verify(employeeRepository, never()).save(any());
    }

    private Employee employee(Business owningBusiness) {
        return Employee.builder()
                .id(UUID.randomUUID())
                .firstName("John")
                .lastName("Doe")
                .enabled(true)
                .business(owningBusiness)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
