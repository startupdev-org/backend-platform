package com.platform.repository;

import com.platform.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {
    Optional<Review> findByBookingId(UUID bookingId);

    @Query("SELECT r FROM Review r WHERE r.booking.priceEntry.employee.business.id = :businessId")
    List<Review> findByBusinessId(@Param("businessId") UUID businessId);

    @Query("SELECT AVG(r.ratingOverall) FROM Review r WHERE r.booking.priceEntry.employee.business.id = :businessId")
    Double getAverageRatingByBusiness(@Param("businessId") UUID businessId);
}
