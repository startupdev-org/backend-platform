package com.platform.repository;

import com.platform.entity.Business;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BusinessRepository extends JpaRepository<Business, UUID>, JpaSpecificationExecutor<Business> {
    Optional<Business> findBySlug(String slug);
    List<Business> findByCity(String city);
    List<Business> findByOwnerId(UUID ownerId);

    Page<Business> findByCity(String city, Pageable pageable);

    // Deterministic ordering: whoami used to index into an unordered result.
    List<Business> findByOwnerIdOrderByCreatedAtAsc(UUID ownerId);

    boolean existsByOwnerId(UUID ownerId);

    // The city / rating / category filters now compose through
    // com.platform.repository.spec.BusinessSpecifications rather than a fixed
    // findByFilters query, so any combination of them reaches the database.

    List<Business> findByNameContainingIgnoreCase(String businessName);

    Page<Business> findByNameContainingIgnoreCase(String businessName, Pageable pageable);
}
