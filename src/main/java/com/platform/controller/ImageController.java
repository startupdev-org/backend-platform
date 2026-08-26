package com.platform.controller;

import com.platform.dto.business.BusinessResponseDTO;
import com.platform.dto.employee.EmployeeResponseDTO;
import com.platform.dto.image.AttachImageRequestDTO;
import com.platform.dto.image.UploadUrlRequestDTO;
import com.platform.dto.image.UploadUrlResponseDTO;
import com.platform.entity.User;
import com.platform.service.ImageService;
import com.platform.service.UserService;
import com.platform.storage.ImageTarget;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Two-step image upload. The client asks for an upload URL, PUTs the file to it, then
 * calls the attachment endpoint with the returned storage key.
 */
@Tag(name = "Images", description = "Business and employee image uploads")
@RestController
@RequestMapping("/api/business/{businessId}")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class ImageController {

    private final ImageService imageService;
    private final UserService userService;

    // ── Business logo / cover ─────────────────────────────────────────────────

    @Operation(summary = "Get a presigned URL for a business logo or cover image")
    @ApiResponse(responseCode = "200", description = "Upload URL generated")
    @ApiResponse(responseCode = "400", description = "Unsupported image type")
    @ApiResponse(responseCode = "403", description = "Not the owner of this business")
    @PostMapping("/images/upload-url")
    public ResponseEntity<UploadUrlResponseDTO> createBusinessUploadUrl(
            @PathVariable UUID businessId,
            @RequestParam ImageTarget target,
            @Valid @RequestBody UploadUrlRequestDTO request,
            Authentication authentication) {
        User currentUser = currentUser(authentication);
        return ResponseEntity.ok(imageService.createBusinessUploadUrl(
                businessId, target, request.getContentType(), currentUser));
    }

    @Operation(summary = "Attach an uploaded logo or cover image to the business")
    @ApiResponse(responseCode = "200", description = "Image attached")
    @ApiResponse(responseCode = "400", description = "Key does not belong to this business, or no file was uploaded")
    @ApiResponse(responseCode = "403", description = "Not the owner of this business")
    @PutMapping("/images")
    public ResponseEntity<BusinessResponseDTO> attachBusinessImage(
            @PathVariable UUID businessId,
            @Parameter(description = "Target scope of the image", example = "LOGO / COVER")
            @RequestParam ImageTarget target,
            @Valid @RequestBody AttachImageRequestDTO request,
            Authentication authentication) {
        User currentUser = currentUser(authentication);
        return ResponseEntity.ok(imageService.
                attachBusinessImage(businessId, target, request.getStorageKey(), currentUser));
    }

    @Operation(summary = "Remove the business logo or cover image")
    @DeleteMapping("/images")
    public ResponseEntity<BusinessResponseDTO> clearBusinessImage(
            @PathVariable UUID businessId,
            @RequestParam ImageTarget target,
            Authentication authentication) {
        User currentUser = currentUser(authentication);
        return ResponseEntity.ok(imageService.clearBusinessImage(businessId, target, currentUser));
    }

    // ── Employee photo ────────────────────────────────────────────────────────

    @Operation(summary = "Get a presigned URL for an employee photo")
    @PostMapping("/employee/{employeeId}/images/upload-url")
    public ResponseEntity<UploadUrlResponseDTO> createEmployeePhotoUploadUrl(
            @PathVariable UUID businessId,
            @PathVariable UUID employeeId,
            @Valid @RequestBody UploadUrlRequestDTO request,
            Authentication authentication) {
        User currentUser = currentUser(authentication);
        return ResponseEntity.ok(imageService.createEmployeePhotoUploadUrl(
                businessId, employeeId, request.getContentType(), currentUser));
    }

    @Operation(summary = "Attach an uploaded photo to the employee")
    @PutMapping("/employee/{employeeId}/images")
    public ResponseEntity<EmployeeResponseDTO> attachEmployeePhoto(
            @PathVariable UUID businessId,
            @PathVariable UUID employeeId,
            @Valid @RequestBody AttachImageRequestDTO request,
            Authentication authentication) {
        User currentUser = currentUser(authentication);
        return ResponseEntity.ok(imageService.attachEmployeePhoto(
                businessId, employeeId, request.getStorageKey(), currentUser));
    }

    @Operation(summary = "Remove the employee photo")
    @DeleteMapping("/employee/{employeeId}/images")
    public ResponseEntity<EmployeeResponseDTO> clearEmployeePhoto(
            @PathVariable UUID businessId,
            @PathVariable UUID employeeId,
            Authentication authentication) {
        User currentUser = currentUser(authentication);
        return ResponseEntity.ok(imageService.clearEmployeePhoto(businessId, employeeId, currentUser));
    }

    private User currentUser(Authentication authentication) {
        return userService.getUserByUsername(authentication.getName());
    }
}
