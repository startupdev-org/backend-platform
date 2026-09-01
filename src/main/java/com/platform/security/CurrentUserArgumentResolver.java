package com.platform.security;

import com.platform.entity.User;
import com.platform.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves a {@code @CurrentUser User} controller parameter to the authenticated
 * caller (BP-40), via the one current-user path -
 * {@link UserService#getUser()}, which reads the principal from the
 * {@code SecurityContextHolder} and loads the row by email.
 *
 * <p>Registered in {@link com.platform.config.WebMvcConfig}.
 */
@Component
@RequiredArgsConstructor
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final UserService userService;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && User.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        // Throws AuthenticationCredentialsNotFoundException for an anonymous caller
        // rather than returning null - see @CurrentUser javadoc.
        return userService.getUser();
    }
}
