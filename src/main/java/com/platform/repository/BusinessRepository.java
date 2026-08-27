package com.platform.repository;

import com.platform.entity.Business;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BusinessRepository extends JpaRepository<Business, UUID> {
    Optional<Business> findBySlug(String slug);
    List<Business> findByCity(String city);
    List<Business> findByBusinessCategory(String category);
    List<Business> findByOwnerId(UUID ownerId);

    Page<Business> findByCity(String city, Pageable pageable);
    Page<Business> findByBusinessCategory(String category, Pageable pageable);

    // Deterministic ordering: whoami used to index into an unordered result.
    List<Business> findByOwnerIdOrderByCreatedAtAsc(UUID ownerId);

    boolean existsByOwnerId(UUID ownerId);

    @Query("SELECT b FROM Business b WHERE " +
           "LOWER(b.city) LIKE LOWER(CONCAT('%', :city, '%')) AND " +
           "b.ratingOverall >= :minRating")
    List<Business> findByFilters(@Param("city") String city, @Param("minRating") Double minRating);

    // The count query is spelled out so the sort added by the Pageable is not carried
    // into it - Postgres rejects ORDER BY on an aggregate-only select.
    @Query(value = "SELECT b FROM Business b WHERE " +
           "LOWER(b.city) LIKE LOWER(CONCAT('%', :city, '%')) AND " +
           "b.ratingOverall >= :minRating",
           countQuery = "SELECT COUNT(b) FROM Business b WHERE " +
           "LOWER(b.city) LIKE LOWER(CONCAT('%', :city, '%')) AND " +
           "b.ratingOverall >= :minRating")
    Page<Business> findByFilters(@Param("city") String city, @Param("minRating") Double minRating, Pageable pageable);

    List<Business> findByNameContainingIgnoreCase(String businessName);

    Page<Business> findByNameContainingIgnoreCase(String businessName, Pageable pageable);
}
