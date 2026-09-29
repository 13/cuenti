package com.cuenti.app.security;

import com.cuenti.app.model.User;
import com.cuenti.app.repository.UserRepository;
import com.cuenti.app.service.GlobalSettingService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates REST API requests from a Bearer token. The token alone is not
 * enough: the user must still exist, be enabled, be allowed to use the API
 * (per user or globally) and the token must carry the user's current token
 * version, so disabling a user, turning API access off or changing the
 * password takes effect immediately instead of when the token expires.
 * Rejected requests stay unauthenticated and get a 401.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final GlobalSettingService globalSettingService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = getTokenFromRequest(request);
        Claims claims = StringUtils.hasText(token) ? tokenProvider.parse(token) : null;

        if (claims != null) {
            userRepository.findByUsername(claims.getSubject())
                    .filter(user -> isAllowed(user, claims))
                    .ifPresent(user -> {
                        List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                                .map(SimpleGrantedAuthority::new).toList();
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        org.springframework.security.core.userdetails.User
                                                .withUsername(user.getUsername())
                                                .password("")
                                                .authorities(authorities)
                                                .build(),
                                        null, authorities);
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    });
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAllowed(User user, Claims claims) {
        return Boolean.TRUE.equals(user.getEnabled())
                && JwtTokenProvider.versionOf(claims) == user.getTokenVersion()
                && (user.isApiEnabled() || globalSettingService.isApiEnabled());
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/");
    }
}
