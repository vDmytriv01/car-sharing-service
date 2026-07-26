package com.vdmytriv.carsharing.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String token = getBearerToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(token, request);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletRequest request) {
        try {
            String email = jwtService.extractEmail(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);
            UsernamePasswordAuthenticationToken authentication =
                    UsernamePasswordAuthenticationToken.authenticated(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
            authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request)
            );
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        } catch (JwtException | AuthenticationException | IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();
        }
    }

    private String getBearerToken(HttpServletRequest request) {
        String authorizationHeader = request.getHeader("Authorization");
        if (!StringUtils.hasText(authorizationHeader)
                || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length());
        return StringUtils.hasText(token) ? token : null;
    }
}
