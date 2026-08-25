package com.platform.repository;

import com.platform.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {
    @Query("SELECT b FROM Booking b WHERE b.priceEntry.employee.id = :employeeId")
    List<Booking> findByEmployeeId(@Param("employeeId") UUID employeeId);

    List<Booking> findByStatus(Booking.BookingStatus status);

    @Query("SELECT b FROM Booking b WHERE b.priceEntry.employee.id = :employeeId " +
           "AND b.startTime >= :startDate AND b.startTime < :endDate")
    List<Booking> findByEmployeeAndDateRange(
            @Param("employeeId") UUID employeeId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT b FROM Booking b WHERE b.priceEntry.employee.business.id = :businessId " +
           "AND b.status = :status")
    List<Booking> findByBusinessAndStatus(
            @Param("businessId") UUID businessId,
            @Param("status") Booking.BookingStatus status);

    @Query("SELECT COUNT(b) FROM Booking b WHERE b.priceEntry.employee.business.id = :businessId " +
           "AND b.status = 'COMPLETED'")
    Long countCompletedByBusiness(@Param("businessId") UUID businessId);

    @Query("SELECT b FROM Booking b WHERE b.priceEntry.service.id = :serviceId")
    List<Booking> findByProvidedServiceId(@Param("serviceId") UUID serviceId);
}
