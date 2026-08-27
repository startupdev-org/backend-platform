package com.platform.utils;

import static org.junit.jupiter.api.Assertions.*;

import com.platform.entity.Business;
import com.platform.entity.User;
import com.platform.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

class BusinessOwnershipValidatorTest {

    private User user(User.UserRole role) {
        return User.builder().id(UUID.randomUUID()).role(role).build();
    }

    private Business businessOwnedBy(User owner) {
        return Business.builder().id(UUID.randomUUID()).owner(owner).build();
    }

    @Test
    void assertOwner_ownerPasses() {
        User owner = user(User.UserRole.BUSINESS_ADMIN);
        assertDoesNotThrow(() -> BusinessOwnershipValidator.assertOwner(businessOwnedBy(owner), owner));
    }

    @Test
    void assertOwner_platformAdminNonOwnerPasses() {
        User owner = user(User.UserRole.BUSINESS_ADMIN);
        User admin = user(User.UserRole.PLATFORM_ADMIN);
        assertDoesNotThrow(() -> BusinessOwnershipValidator.assertOwner(businessOwnedBy(owner), admin));
    }

    @Test
    void assertOwner_nonOwnerBusinessAdminThrows() {
        User owner = user(User.UserRole.BUSINESS_ADMIN);
        User other = user(User.UserRole.BUSINESS_ADMIN);
        assertThrows(BusinessException.class,
                () -> BusinessOwnershipValidator.assertOwner(businessOwnedBy(owner), other));
    }
}
