package com.platform.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds the authenticated {@link com.platform.entity.User} to a controller method
 * parameter (BP-40).
 *
 * <p>Before this, "who is calling" was resolved three different ways -
 * {@code SecurityContextHolder} inside the service, a raw
 * {@code userService.getUserByUsername(authentication.getName())} in the controller,
 * and a per-controller {@code currentUser(Authentication)} helper.
 * {@code BusinessController} alone used all three. {@link CurrentUserArgumentResolver}
 * is now the single mechanism: a parameter marked {@code @CurrentUser} is populated
 * from {@link com.platform.service.UserService#getUser()}.
 *
 * <p>The endpoint must already require authentication (every rule in
 * {@code SecurityConfig} ends in {@code .anyRequest().authenticated()}); an
 * unauthenticated request never reaches a resolver, and if one somehow did the
 * resolver throws {@code AuthenticationCredentialsNotFoundException} rather than
 * injecting {@code null}.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUser {
}
