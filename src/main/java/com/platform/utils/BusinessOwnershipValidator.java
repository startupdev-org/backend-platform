package com.platform.utils;

import com.platform.entity.Business;
import com.platform.entity.User;
import com.platform.exception.BusinessException;

/**
 * Shared business-ownership guard for mutating operations.
 *
 * <p>Encodes the same rule the per-service copies use (see
 * {@code LocationService.validateBusinessOwnership}): the caller must own the
 * business, unless they are a {@code PLATFORM_ADMIN}.
 */
public final class BusinessOwnershipValidator {

    private BusinessOwnershipValidator() {}

    /**
     * Passes if {@code currentUser} owns {@code business} or is a
     * {@code PLATFORM_ADMIN}; otherwise throws {@link BusinessException} (→ 403).
     */
    public static void assertOwner(Business business, User currentUser) {
        if (business.isNotOwner(currentUser)
                && !currentUser.getRole().equals(User.UserRole.PLATFORM_ADMIN)) {
            throw new BusinessException("Unauthorized");
        }
    }
}
