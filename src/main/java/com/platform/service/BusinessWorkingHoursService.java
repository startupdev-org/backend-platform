package com.platform.service;

import com.platform.dto.business.CreateWorkingHoursRequest;
import com.platform.dto.business.BusinessWorkingHoursDTO;
import com.platform.entity.Business;
import com.platform.entity.BusinessWorkingHours;
import com.platform.entity.User;
import com.platform.exception.BadRequestException;
import com.platform.exception.BusinessException;
import com.platform.exception.ConflictException;
import com.platform.exception.ResourceNotFoundException;
import com.platform.repository.BusinessRepository;
import com.platform.repository.BusinessWorkingHoursRepository;
import com.platform.utils.BusinessOwnershipValidator;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class BusinessWorkingHoursService {

    private final BusinessRepository businessRepository;
    private final BusinessWorkingHoursRepository workingHoursRepository;

    private static final String BUSINESS_NOT_FOUND = "Business not found";
    private static final String WORKING_HOURS_NOT_FOUND = "Working hours not found";

    public BusinessWorkingHoursDTO create(UUID businessId,
                                          CreateWorkingHoursRequest request,
                                          User currentUser) {

        validateTimeRange(request.getOpenTime(), request.getCloseTime());

        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException(BUSINESS_NOT_FOUND));

        BusinessOwnershipValidator.assertOwner(business, currentUser);

        validateNoOverlap(
                businessId,
                request.getDayOfWeek(),
                request.getOpenTime(),
                request.getCloseTime(),
                null
        );

        BusinessWorkingHours workingHours = new BusinessWorkingHours(
                business,
                request.getDayOfWeek(),
                request.getOpenTime(),
                request.getCloseTime()
        );

        workingHoursRepository.save(workingHours);

        return BusinessWorkingHoursDTO.builder()
                .openTime(workingHours.getOpenTime())
                .dayOfWeek(workingHours.getDayOfWeek())
                .closeTime(workingHours.getCloseTime())
                .build();

    }

    @Transactional
    public List<BusinessWorkingHoursDTO> getByBusiness(UUID businessId) {
        return workingHoursRepository.findByBusinessId(businessId)
                .stream()
                .map(this::mapToDTO)
                .toList();
    }

    private BusinessWorkingHoursDTO mapToDTO(BusinessWorkingHours entity) {
        return BusinessWorkingHoursDTO.builder()
                .id(entity.getId())
                .dayOfWeek(entity.getDayOfWeek())
                .openTime(entity.getOpenTime())
                .closeTime(entity.getCloseTime())
                .build();
    }

    public BusinessWorkingHoursDTO update(UUID businessId,
                                          Long id,
                                          CreateWorkingHoursRequest request,
                                          User currentUser) {

        validateTimeRange(request.getOpenTime(), request.getCloseTime());

        BusinessWorkingHours existing = workingHoursRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(WORKING_HOURS_NOT_FOUND));

        if (!existing.getBusiness().getId().equals(businessId)) {
            throw new BusinessException("Invalid business for this working hours entry");
        }

        BusinessOwnershipValidator.assertOwner(existing.getBusiness(), currentUser);

        validateNoOverlap(
                businessId,
                request.getDayOfWeek(),
                request.getOpenTime(),
                request.getCloseTime(),
                id
        );

        existing.setDayOfWeek(request.getDayOfWeek());
        existing.setOpenTime(request.getOpenTime());
        existing.setCloseTime(request.getCloseTime());

        return mapToDTO(workingHoursRepository.save(existing));
    }

    public void delete(UUID businessId, Long id, User currentUser) {

        BusinessWorkingHours existing = workingHoursRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(WORKING_HOURS_NOT_FOUND));

        if (!existing.getBusiness().getId().equals(businessId)) {
            throw new BusinessException("Invalid business for this working hours entry");
        }

        BusinessOwnershipValidator.assertOwner(existing.getBusiness(), currentUser);

        workingHoursRepository.delete(existing);
    }

    // =========================
    // Validation Logic
    // =========================

    private void validateTimeRange(LocalTime open, LocalTime close) {
        if (!open.isBefore(close)) {
            throw new BadRequestException("Open time must be before close time");
        }
    }

    private void validateNoOverlap(UUID businessId,
                                   DayOfWeek dayOfWeek,
                                   LocalTime newStart,
                                   LocalTime newEnd,
                                   Long excludeId) {

        List<BusinessWorkingHours> existing =
                workingHoursRepository.findByBusinessIdAndDayOfWeek(businessId, dayOfWeek);

        for (BusinessWorkingHours hours : existing) {

            if (excludeId != null && hours.getId().equals(excludeId)) {
                continue;
            }

            boolean overlaps =
                    newStart.isBefore(hours.getCloseTime()) &&
                            newEnd.isAfter(hours.getOpenTime());

                if (overlaps) {
                throw new ConflictException(
                        "Working hours overlap with existing interval"
                );
            }
        }
    }
}
