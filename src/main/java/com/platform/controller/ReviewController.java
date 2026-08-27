package com.platform.controller;

import com.platform.dto.review.ReviewRequestDTO;
import com.platform.dto.review.ReviewResponseDTO;
import com.platform.entity.User;
import com.platform.service.ReviewService;
import com.platform.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@Validated   // makes the @Size on the reply request param below take effect
@RequestMapping("/api/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final UserService userService;

    @PostMapping("/booking/{bookingId}")
    public ResponseEntity<ReviewResponseDTO> createReview(
            @PathVariable UUID bookingId,
            @Valid @RequestBody ReviewRequestDTO request) {
        ReviewResponseDTO review = reviewService.createReview(bookingId, request);
        return new ResponseEntity<>(review, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReviewResponseDTO> getReview(@PathVariable UUID id) {
        ReviewResponseDTO review = reviewService.getReview(id);
        return ResponseEntity.ok(review);
    }

    @GetMapping("/business/{businessId}")
    public ResponseEntity<List<ReviewResponseDTO>> getBusinessReviews(@PathVariable UUID businessId) {
        List<ReviewResponseDTO> reviews = reviewService.getBusinessReviews(businessId);
        return ResponseEntity.ok(reviews);
    }

    @PatchMapping("/{id}/reply")
    public ResponseEntity<ReviewResponseDTO> addBusinessReply(
            @PathVariable UUID id,
            @RequestParam @Size(max = 1000, message = "Reply must not exceed 1000 characters") String reply,
            Authentication authentication) {
        User currentUser = userService.getUserByUsername(authentication.getName());
        ReviewResponseDTO review = reviewService.addBusinessReply(id, reply, currentUser);
        return ResponseEntity.ok(review);
    }

    @GetMapping("/business/{businessId}/average")
    public ResponseEntity<Double> getAverageRating(@PathVariable UUID businessId) {
        Double average = reviewService.getAverageRating(businessId);
        return ResponseEntity.ok(average);
    }
}
