package com.platform.controller;

import com.platform.dto.review.ReviewRequestDTO;
import com.platform.dto.review.ReviewResponseDTO;
import com.platform.entity.User;
import com.platform.security.CurrentUser;
import com.platform.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Review", description = "Customer review endpoints")
@RestController
@Validated   // makes the @Size on the reply request param below take effect
@RequestMapping("/api/review")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "Create a review",
            description = "Adds a review to a booking. Only a COMPLETED booking can be reviewed, "
                    + "and only once.")
    @ApiResponse(responseCode = "201", description = "Review created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "403", description = "Booking is not completed, or it already has a review")
    @ApiResponse(responseCode = "404", description = "Booking not found")
    @PostMapping("/booking/{bookingId}")
    public ResponseEntity<ReviewResponseDTO> createReview(
            @Parameter(description = "Booking UUID", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID bookingId,
            @Valid @RequestBody ReviewRequestDTO request) {
        ReviewResponseDTO review = reviewService.createReview(bookingId, request);
        return new ResponseEntity<>(review, HttpStatus.CREATED);
    }

    @Operation(summary = "Get a review by ID", description = "Returns a single review")
    @ApiResponse(responseCode = "200", description = "Review found")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "404", description = "Review not found")
    @GetMapping("/{id}")
    public ResponseEntity<ReviewResponseDTO> getReview(
            @Parameter(description = "Review UUID")
            @PathVariable UUID id) {
        ReviewResponseDTO review = reviewService.getReview(id);
        return ResponseEntity.ok(review);
    }

    @Operation(summary = "List a business's reviews",
            description = "Returns every review left for the business")
    @ApiResponse(responseCode = "200", description = "Reviews retrieved successfully")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @GetMapping("/business/{businessId}")
    public ResponseEntity<List<ReviewResponseDTO>> getBusinessReviews(
            @Parameter(description = "Business UUID")
            @PathVariable UUID businessId) {
        List<ReviewResponseDTO> reviews = reviewService.getBusinessReviews(businessId);
        return ResponseEntity.ok(reviews);
    }

    @Operation(summary = "Reply to a review",
            description = "Attaches the business's public reply to a review. Only the owner of the "
                    + "reviewed business may reply.")
    @ApiResponse(responseCode = "200", description = "Reply saved successfully")
    @ApiResponse(responseCode = "400", description = "Reply exceeds 1000 characters")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "403", description = "Not the owner of the reviewed business")
    @ApiResponse(responseCode = "404", description = "Review not found")
    @PatchMapping("/{id}/reply")
    public ResponseEntity<ReviewResponseDTO> addBusinessReply(
            @Parameter(description = "Review UUID")
            @PathVariable UUID id,
            @Parameter(description = "Reply text, at most 1000 characters")
            @RequestParam @Size(max = 1000, message = "Reply must not exceed 1000 characters") String reply,
            @CurrentUser User currentUser) {
        ReviewResponseDTO review = reviewService.addBusinessReply(id, reply, currentUser);
        return ResponseEntity.ok(review);
    }

    @Operation(summary = "Get a business's average rating",
            description = "Returns the mean rating across the business's reviews")
    @ApiResponse(responseCode = "200", description = "Average rating retrieved successfully")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @GetMapping("/business/{businessId}/average")
    public ResponseEntity<Double> getAverageRating(
            @Parameter(description = "Business UUID")
            @PathVariable UUID businessId) {
        Double average = reviewService.getAverageRating(businessId);
        return ResponseEntity.ok(average);
    }
}
