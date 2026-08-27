package com.platform.utils;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TimeSlotGeneratorTest {

    private static final LocalDateTime DAY = LocalDateTime.of(2026, 8, 31, 0, 0);

    @Test
    void multipleIntervals_gridStepsEvery30MinutesAndStopsWhenServiceNoLongerFits() {
        List<LocalDateTime> slots = TimeSlotGenerator.generateAvailableSlots(
                DAY, 60, LocalTime.of(9, 0), LocalTime.of(12, 0));

        assertEquals(List.of(
                DAY.withHour(9).withMinute(0),
                DAY.withHour(9).withMinute(30),
                DAY.withHour(10).withMinute(0),
                DAY.withHour(10).withMinute(30),
                DAY.withHour(11).withMinute(0)
        ), slots);
    }

    @Test
    void serviceLongerThanWindow_returnsNoSlots() {
        List<LocalDateTime> slots = TimeSlotGenerator.generateAvailableSlots(
                DAY, 120, LocalTime.of(9, 0), LocalTime.of(10, 0));

        assertTrue(slots.isEmpty());
    }

    @Test
    void serviceExactlyFillsWindow_returnsSingleSlotAtOpen() {
        List<LocalDateTime> slots = TimeSlotGenerator.generateAvailableSlots(
                DAY, 90, LocalTime.of(9, 0), LocalTime.of(10, 30));

        assertEquals(List.of(DAY.withHour(9).withMinute(0)), slots);
    }

    @Test
    void lastSlotEndsExactlyAtClose_noPartialSlotBeyondClose() {
        List<LocalDateTime> slots = TimeSlotGenerator.generateAvailableSlots(
                DAY, 30, LocalTime.of(9, 0), LocalTime.of(10, 15));

        assertEquals(List.of(
                DAY.withHour(9).withMinute(0),
                DAY.withHour(9).withMinute(30)
        ), slots);
        assertFalse(slots.contains(DAY.withHour(10).withMinute(0)));
    }

    @Test
    void emptyOrInvertedInterval_returnsNoSlots() {
        assertTrue(TimeSlotGenerator.generateAvailableSlots(
                DAY, 30, LocalTime.of(9, 0), LocalTime.of(9, 0)).isEmpty());
        assertTrue(TimeSlotGenerator.generateAvailableSlots(
                DAY, 30, LocalTime.of(18, 0), LocalTime.of(9, 0)).isEmpty());
    }

    @Test
    void nonPositiveDuration_returnsNoSlots() {
        assertTrue(TimeSlotGenerator.generateAvailableSlots(
                DAY, 0, LocalTime.of(9, 0), LocalTime.of(17, 0)).isEmpty());
        assertTrue(TimeSlotGenerator.generateAvailableSlots(
                DAY, -30, LocalTime.of(9, 0), LocalTime.of(17, 0)).isEmpty());
    }
}
