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

    // ── List-view fetch graph ─────────────────────────────────────────────────
    // BookingService.toDTO dereferences priceEntry -> employee and priceEntry ->
    // service, and Booking.review is a non-owning @OneToOne that Hibernate cannot
    // proxy, so a plain findAll() over the list fans out to ~2N+1 queries. These
    // queries pull the whole graph the DTO needs in one statement. See BP-53.
    String LIST_FETCH_GRAPH =
            "SELECT b FROM Booking b " +
            "JOIN FETCH b.priceEntry p " +
            "JOIN FETCH p.employee " +
            "JOIN FETCH p.service " +
            "LEFT JOIN FETCH b.review ";

    @Query(LIST_FETCH_GRAPH)
    List<Booking> findAllForListing();

    @Query(LIST_FETCH_GRAPH + "WHERE p.employee.id = :employeeId")
    List<Booking> findByEmployeeIdForListing(@Param("employeeId") UUID employeeId);

    @Query(LIST_FETCH_GRAPH + "WHERE b.status = :status")
    List<Booking> findByStatusForListing(@Param("status") Booking.BookingStatus status);

    @Query(LIST_FETCH_GRAPH + "WHERE p.employee.id = :employeeId AND b.status = :status")
    List<Booking> findByEmployeeIdAndStatusForListing(
            @Param("employeeId") UUID employeeId,
            @Param("status") Booking.BookingStatus status);

    @Query("SELECT b FROM Booking b WHERE b.priceEntry.employee.id = :employeeId")
    List<Booking> findByEmployeeId(@Param("employeeId") UUID employeeId);

    List<Booking> findByStatus(Booking.BookingStatus status);

    @Query(LIST_FETCH_GRAPH +
           "WHERE p.employee.id = :employeeId AND b.startTime >= :startDate AND b.startTime < :endDate")
    List<Booking> findByEmployeeAndDateRange(
            @Param("employeeId") UUID employeeId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query(LIST_FETCH_GRAPH + "WHERE p.employee.business.id = :businessId AND b.status = :status")
    List<Booking> findByBusinessAndStatus(
            @Param("businessId") UUID businessId,
            @Param("status") Booking.BookingStatus status);

    @Query("SELECT COUNT(b) FROM Booking b WHERE b.priceEntry.employee.business.id = :businessId " +
           "AND b.status = 'COMPLETED'")
    Long countCompletedByBusiness(@Param("businessId") UUID businessId);

    @Query("SELECT b FROM Booking b WHERE b.priceEntry.service.id = :serviceId")
    List<Booking> findByProvidedServiceId(@Param("serviceId") UUID serviceId);
}
