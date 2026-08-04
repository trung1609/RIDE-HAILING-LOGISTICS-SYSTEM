package com.trung.userdriverservice.service.impl;

import com.trung.userdriverservice.dto.request.LoginRequest;
import com.trung.userdriverservice.dto.response.ApiResponse;
import com.trung.userdriverservice.dto.response.LoginResponse;
import com.trung.userdriverservice.entity.User;
import com.trung.userdriverservice.exception.InvalidCredentialsException;
import com.trung.userdriverservice.exception.ResourceNotFoundException;
import com.trung.userdriverservice.mapper.UserMapper;
import com.trung.userdriverservice.repository.UserRepository;
import com.trung.userdriverservice.security.JwtTokenProvider;
import com.trung.userdriverservice.security.RefreshTokenService;
import com.trung.userdriverservice.security.UserPrincipal;
import com.trung.userdriverservice.service.AuthService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final UserMapper userMapper;
    private final StringRedisTemplate redisTemplate;

    @Override
    public ApiResponse<LoginResponse> login(LoginRequest request) throws ResourceNotFoundException, InvalidCredentialsException {
        try {

            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getPhoneNumber(), request.getPassword())
            );
            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            User user = userPrincipal.getUser();

            // 3. Tạo các token
            String accessToken = jwtTokenProvider.generateAccessToken(user);
            String refreshToken = refreshTokenService.generateAndSaveRefreshToken(user.getPhoneNumber());
            LoginResponse loginResponse = LoginResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .user(userMapper.toUserResponse(user))
                    .build();

            return ApiResponse.<LoginResponse>builder()
                    .success(true)
                    .message("Đăng nhập thành công")
                    .data(loginResponse)
                    .timestamp(LocalDateTime.now())
                    .error(null)
                    .build();
        } catch (AuthenticationException ex) {
            throw new InvalidCredentialsException("Số điện thoại hoặc mật khẩu không hợp lệ.");
        }
    }

    @Override
    public ApiResponse<LoginResponse> refresh(String oldRefreshToken, String oldAccessToken) throws InvalidCredentialsException, ResourceNotFoundException {
        String phoneNumber = refreshTokenService.getPhoneNumberFromRefreshToken(oldRefreshToken);
        if (!refreshTokenService.validateRefreshToken(phoneNumber, oldRefreshToken)) {
            throw new InvalidCredentialsException("Refresh token không hợp lệ hoặc đã hết hạn.");
        }
        if (oldAccessToken != null) {
            blacklistAccessToken(oldAccessToken);
        }
        refreshTokenService.deleteRefreshToken(phoneNumber);

        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại với số điện thoại: " + phoneNumber));

        String newAccessToken = jwtTokenProvider.generateAccessToken(user);
        String newRefreshToken = refreshTokenService.generateAndSaveRefreshToken(phoneNumber);

        LoginResponse loginResponse = LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .user(userMapper.toUserResponse(user))
                .build();

        return ApiResponse.<LoginResponse>builder()
                .success(true)
                .message("Làm mới token thành công")
                .data(loginResponse)
                .timestamp(LocalDateTime.now())
                .error(null)
                .build();
    }

    @Override
    public ApiResponse<String> logout(String accessToken, String refreshToken) {
        String phoneNumber = jwtTokenProvider.getPhoneNumberFromToken(accessToken);

        blacklistAccessToken(accessToken);

        refreshTokenService.deleteRefreshToken(phoneNumber);

        return ApiResponse.<String>builder()
                .success(true)
                .message("Đăng xuất thành công")
                .timestamp(LocalDateTime.now())
                .build();
    }

    private void blacklistAccessToken(String token) {
        try {
            Claims claims = jwtTokenProvider.getClaimsFromToken(token);
            Date expiration = claims.getExpiration();
            long diff = expiration.getTime() - System.currentTimeMillis();

            if (diff > 0) {
                redisTemplate.opsForValue().set(
                        "jwt_blacklist:" + token,
                        "revoked",
                        diff,
                        TimeUnit.MILLISECONDS
                );
            }
        } catch (Exception e) {
        }
    }
}
