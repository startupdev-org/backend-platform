package com.platform.utils;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns an opening interval into a grid of bookable start times.
 *
 * <p>The grid step is fixed at {@link #SLOT_DURATION_MINUTES}. A start time is
 * kept only if the whole service still fits before {@code businessClose}, so a
 * service longer than the remaining window yields no slot rather than a slot that
 * runs past closing.
 *
 * <p>This class knows nothing about working-hours rows or existing bookings -
 * callers pass one concrete interval and subtract taken slots themselves. See
 * {@code AvailabilityService}.
 */
public final class TimeSlotGenerator {

    private static final int SLOT_DURATION_MINUTES = 30;

    private TimeSlotGenerator() {}

    /**
     * Bookable start times inside {@code [businessOpen, businessClose)} on the day
     * of {@code date}, for a service of {@code serviceDurationMinutes}.
     *
     * @return an empty list if the service does not fit the interval, if the
     *         interval is empty, or if {@code serviceDurationMinutes} is not positive
     */
    public static List<LocalDateTime> generateAvailableSlots(LocalDateTime date, int serviceDurationMinutes,
                                                             LocalTime businessOpen, LocalTime businessClose) {
        List<LocalDateTime> slots = new ArrayList<>();

        if (serviceDurationMinutes <= 0 || !businessOpen.isBefore(businessClose)) {
            return slots;
        }

        LocalDateTime startOfDay = date.withHour(businessOpen.getHour())
                .withMinute(businessOpen.getMinute())
                .withSecond(0)
                .withNano(0);

        LocalDateTime endOfDay = date.withHour(businessClose.getHour())
                .withMinute(businessClose.getMinute())
                .withSecond(0)
                .withNano(0);

        LocalDateTime currentSlot = startOfDay;

        while (!currentSlot.plusMinutes(serviceDurationMinutes).isAfter(endOfDay)) {
            slots.add(currentSlot);
            currentSlot = currentSlot.plusMinutes(SLOT_DURATION_MINUTES);
        }

        return slots;
    }
}
