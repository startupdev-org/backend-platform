package com.platform.dto.availability;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Bookable start times for one employee, one service, one day.
 *
 * <p>{@code availableSlots} is empty when the business has no working-hours row
 * for that weekday - the public booking page should show "closed", not a default
 * 09:00-19:00 grid.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AvailabilityResponseDTO {

    private UUID employeeId;
    private UUID serviceId;
    private LocalDate date;
    private int serviceDurationMinutes;
    private List<LocalDateTime> availableSlots;
}
