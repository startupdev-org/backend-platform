package com.platform.repository;

import com.platform.entity.ProvidedService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ServiceRepository extends JpaRepository<ProvidedService, UUID> {
    List<ProvidedService> findByBusinessId(UUID businessId);

    List<ProvidedService> findByBusinessIdIn(Collection<UUID> businessIds);
    List<ProvidedService> findByBusinessIdAndActive(UUID businessId, Boolean active);
}
