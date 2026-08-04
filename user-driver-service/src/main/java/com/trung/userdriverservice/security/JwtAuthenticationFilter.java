package com.trung.userdriverservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService customUserDetailsService;
    private final StringRedisTemplate redisTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String token = getTokenFromRequest(request);
        try {
            if (token != null &&
                    jwtTokenProvider.validateToken(token) &&
                    jwtTokenProvider.getTypeFromToken(token).equals("access")) {
                Boolean isBlacklisted = redisTemplate.hasKey("jwt_blacklist:" + token);
                if (Boolean.TRUE.equals(isBlacklisted)) {
                    log.warn("Token đã nằm trong danh sách vô hiệu hóa (Blacklist). Request bị chặn.");
                    filterChain.doFilter(request, response);
                    return;
                }
                String phoneNumber = jwtTokenProvider.getPhoneNumberFromToken(token);
                UserDetails userDetails = customUserDetailsService.loadUserByUsername(phoneNumber);
                if (userDetails != null && userDetails.isEnabled()) {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (Exception e) {
            log.error("Không thể thiết lập xác thực người dùng trong Security Context", e);
        }
        filterChain.doFilter(request, response);
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        if (request.getHeader("Authorization") != null && request.getHeader("Authorization").startsWith("Bearer ")) {
            return request.getHeader("Authorization").substring(7);
        }
        return null;
    }
}
