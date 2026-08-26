package com.platform.service;

import static org.junit.jupiter.api.Assertions.*;


import com.platform.dto.auth.LoginRequest;
import com.platform.dto.auth.LoginResponse;
import com.platform.dto.auth.RegisterRequest;
import com.platform.dto.business.BusinessWorkingHoursDTO;
import com.platform.dto.business.CreateWorkingHoursRequest;
import com.platform.entity.Business;
import com.platform.entity.BusinessWorkingHours;
import com.platform.entity.User;
import com.platform.exception.BadRequestException;
import com.platform.exception.BusinessException;
import com.platform.exception.ConflictException;
import com.platform.exception.ResourceNotFoundException;
import com.platform.repository.BusinessRepository;
import com.platform.repository.BusinessWorkingHoursRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Collections;
import java.util.Optional;
import java.util.*;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BusinessWorkingHoursServiceTest {

    @InjectMocks
    private BusinessWorkingHoursService service;

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private BusinessWorkingHoursRepository workingHoursRepository;

    private Business business;

    @BeforeEach
    void setUp() {
        business = Business.builder()
                .id(UUID.randomUUID())
                .name("Test Business")
                .build();
    }

    @Test
    void create_shouldSaveWorkingHours() {
        CreateWorkingHoursRequest request = new CreateWorkingHoursRequest();
        request.setDayOfWeek(DayOfWeek.MONDAY);
        request.setOpenTime(LocalTime.of(9, 0));
        request.setCloseTime(LocalTime.of(17, 0));

        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));
        when(workingHoursRepository.findByBusinessIdAndDayOfWeek(
                business.getId(), DayOfWeek.MONDAY))
                .thenReturn(Collections.emptyList());

        BusinessWorkingHours saved = new BusinessWorkingHours(business, DayOfWeek.MONDAY,
                request.getOpenTime(), request.getCloseTime());
        saved.setId(1L);

        when(workingHoursRepository.save(any(BusinessWorkingHours.class))).thenReturn(saved);

        BusinessWorkingHoursDTO result = service.create(business.getId(), request);

        assertNotNull(result);
        assertEquals(DayOfWeek.MONDAY, result.getDayOfWeek());
        assertEquals(request.getOpenTime(), result.getOpenTime());
        assertEquals(request.getCloseTime(), result.getCloseTime());

        verify(workingHoursRepository, times(1)).save(any(BusinessWorkingHours.class));
    }

    @Test
    void create_shouldThrowWhenOpenAfterClose() {
        CreateWorkingHoursRequest request = new CreateWorkingHoursRequest();
        request.setDayOfWeek(DayOfWeek.TUESDAY);
        request.setOpenTime(LocalTime.of(18, 0));
        request.setCloseTime(LocalTime.of(9, 0));

        Exception exception = assertThrows(BadRequestException.class, () ->
                service.create(business.getId(), request)
        );

        assertEquals("Open time must be before close time", exception.getMessage());
    }

    @Test
    void create_shouldThrowWhenOverlap() {
        CreateWorkingHoursRequest request = new CreateWorkingHoursRequest();
        request.setDayOfWeek(DayOfWeek.WEDNESDAY);
        request.setOpenTime(LocalTime.of(10, 0));
        request.setCloseTime(LocalTime.of(15, 0));

        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));

        BusinessWorkingHours existing = new BusinessWorkingHours(
                business, DayOfWeek.WEDNESDAY,
                LocalTime.of(12, 0), LocalTime.of(18, 0)
        );
        existing.setId(1L);

        when(workingHoursRepository.findByBusinessIdAndDayOfWeek(
                business.getId(), DayOfWeek.WEDNESDAY))
                .thenReturn(List.of(existing));

        Exception exception = assertThrows(ConflictException.class, () ->
                service.create(business.getId(), request)
        );

        assertEquals("Working hours overlap with existing interval", exception.getMessage());
    }

    @Test
    void create_businessNotFound_throwsNotFound() {
        CreateWorkingHoursRequest request = new CreateWorkingHoursRequest();
        request.setDayOfWeek(DayOfWeek.MONDAY);
        request.setOpenTime(LocalTime.of(9, 0));
        request.setCloseTime(LocalTime.of(17, 0));

        when(businessRepository.findById(business.getId())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                service.create(business.getId(), request)
        );

        verify(workingHoursRepository, never()).save(any());
    }

    @Test
    void getByBusiness_shouldReturnList() {
        BusinessWorkingHours wh1 = new BusinessWorkingHours(business, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(17, 0));
        wh1.setId(1L);
        BusinessWorkingHours wh2 = new BusinessWorkingHours(business, DayOfWeek.TUESDAY,
                LocalTime.of(10, 0), LocalTime.of(16, 0));
        wh2.setId(2L);

        when(workingHoursRepository.findByBusinessId(business.getId()))
                .thenReturn(List.of(wh1, wh2));

        List<BusinessWorkingHoursDTO> result = service.getByBusiness(business.getId());

        assertEquals(2, result.size());
        assertEquals(DayOfWeek.MONDAY, result.get(0).getDayOfWeek());
        assertEquals(DayOfWeek.TUESDAY, result.get(1).getDayOfWeek());
    }

    @Test
    void update_shouldModifyWorkingHours() {
        BusinessWorkingHours existing = new BusinessWorkingHours(
                business, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(17, 0));
        existing.setId(1L);

        CreateWorkingHoursRequest request = new CreateWorkingHoursRequest();
        request.setDayOfWeek(DayOfWeek.MONDAY);
        request.setOpenTime(LocalTime.of(10, 0));
        request.setCloseTime(LocalTime.of(18, 0));

        when(workingHoursRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(workingHoursRepository.findByBusinessIdAndDayOfWeek(
                business.getId(), DayOfWeek.MONDAY))
                .thenReturn(Collections.emptyList());
        when(workingHoursRepository.save(existing)).thenReturn(existing);

        BusinessWorkingHours updated = service.update(business.getId(), 1L, request);

        assertEquals(LocalTime.of(10, 0), updated.getOpenTime());
        assertEquals(LocalTime.of(18, 0), updated.getCloseTime());
    }

    @Test
    void update_workingHoursNotFound_throwsNotFound() {
        CreateWorkingHoursRequest request = new CreateWorkingHoursRequest();
        request.setDayOfWeek(DayOfWeek.MONDAY);
        request.setOpenTime(LocalTime.of(9, 0));
        request.setCloseTime(LocalTime.of(17, 0));

        when(workingHoursRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                service.update(business.getId(), 1L, request)
        );

        verify(workingHoursRepository, never()).save(any());
    }

    @Test
    void delete_shouldRemoveWorkingHours() {
        BusinessWorkingHours existing = new BusinessWorkingHours(
                business, DayOfWeek.FRIDAY,
                LocalTime.of(9, 0), LocalTime.of(17, 0));
        existing.setId(1L);

        when(workingHoursRepository.findById(1L)).thenReturn(Optional.of(existing));

        service.delete(business.getId(), 1L);

        verify(workingHoursRepository, times(1)).delete(existing);
    }

    @Test
    void delete_shouldThrowOnInvalidBusiness() {
        BusinessWorkingHours existing = new BusinessWorkingHours(
                Business.builder().id(UUID.randomUUID()).build(),
                DayOfWeek.FRIDAY,
                LocalTime.of(9, 0), LocalTime.of(17, 0));
        existing.setId(1L);

        when(workingHoursRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThrows(BusinessException.class, () -> service.delete(business.getId(), 1L));
    }

    @Test
    void delete_workingHoursNotFound_throwsNotFound() {
        when(workingHoursRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                service.delete(business.getId(), 1L)
        );

        verify(workingHoursRepository, never()).delete(any());
    }
}
