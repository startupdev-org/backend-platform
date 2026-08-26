package com.platform.repository;

import com.platform.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LocationRepository extends JpaRepository<Location, UUID> {
    List<Location> findByBusinessId(UUID businessId);

    Optional<Location> findByBusinessIdAndIsDefaultLocationTrue(UUID businessId);
    List<Location> findByBusinessIdIn(Collection<UUID> businessIds);
}
