package com.platform.security;

import com.platform.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String jwt = extractToken(request);

        if (StringUtils.hasText(jwt)) {
            Optional<Claims> claims;
            try {
                claims = jwtUtils.parseClaims(jwt);
            } catch (ExpiredJwtException e) {
                // Recorded so RestAuthenticationEntryPoint can say "expired" rather than a
                // generic "authentication required" - that is what lets the frontend decide
                // to silently re-login instead of showing an error.
                request.setAttribute(RestAuthenticationEntryPoint.JWT_ERROR_ATTRIBUTE,
                        RestAuthenticationEntryPoint.JWT_ERROR_EXPIRED);
                claims = Optional.empty();
            }

            if (claims.isPresent()) {
                authenticate(request, claims.get());
            } else if (request.getAttribute(RestAuthenticationEntryPoint.JWT_ERROR_ATTRIBUTE) == null) {
                request.setAttribute(RestAuthenticationEntryPoint.JWT_ERROR_ATTRIBUTE,
                        RestAuthenticationEntryPoint.JWT_ERROR_INVALID);
            }
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, Claims claims) {
        String email = jwtUtils.getUserEmailFromClaims(claims);
        String role = jwtUtils.getRoleFromClaims(claims);

        // A token with no role claim is not usable. Building "ROLE_null" would still fail
        // closed, but as a 403 carrying an authenticated principal - far harder to diagnose
        // than a clean 401.
        if (!StringUtils.hasText(email) || !StringUtils.hasText(role)) {
            log.debug("Token is missing the subject or role claim; rejecting");
            request.setAttribute(RestAuthenticationEntryPoint.JWT_ERROR_ATTRIBUTE,
                    RestAuthenticationEntryPoint.JWT_ERROR_INVALID);
            return;
        }

        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
        var authentication = new UsernamePasswordAuthenticationToken(email, null, authorities);

        // Attaches IP, session info to the auth token — useful for auditing
        authentication.setDetails(
                new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        log.debug("Authenticated user '{}' with role '{}'", email, role);
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
