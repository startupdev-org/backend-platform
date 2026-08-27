package com.platform.repository;

import com.platform.entity.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, UUID> {
    List<Employee> findByBusinessIdAndEnabled(UUID businessId, Boolean enabled);

    Page<Employee> findByBusinessIdAndEnabled(UUID businessId, Boolean enabled, Pageable pageable);

    List<Employee> findByBusinessIdInAndEnabled(Collection<UUID> businessIds, Boolean enabled);
}
