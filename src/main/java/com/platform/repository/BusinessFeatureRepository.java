package com.platform.repository;

import com.platform.entity.BusinessFeature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface BusinessFeatureRepository extends JpaRepository<BusinessFeature, Long> {
    List<BusinessFeature> findByBusinessId(UUID businessId);

    List<BusinessFeature> findByBusinessIdIn(Collection<UUID> businessIds);

    boolean existsByBusinessIdAndName(UUID businessId, String name);
}
