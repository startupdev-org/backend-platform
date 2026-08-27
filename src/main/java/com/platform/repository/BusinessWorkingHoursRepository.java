package com.platform.repository;

import com.platform.entity.BusinessWorkingHours;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.*;


public interface BusinessWorkingHoursRepository extends JpaRepository<BusinessWorkingHours, Long> {
    List<BusinessWorkingHours> findByBusinessIdAndDayOfWeek(
            UUID businessId,
            DayOfWeek dayOfWeek
    );

    List<BusinessWorkingHours> findByBusinessId(UUID businessId);

    List<BusinessWorkingHours> findByBusinessIdIn(Collection<UUID> businessIds);
}
