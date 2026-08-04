package com.trung.userdriverservice.service;

import com.trung.userdriverservice.dto.request.LoginRequest;
import com.trung.userdriverservice.dto.response.ApiResponse;
import com.trung.userdriverservice.dto.response.LoginResponse;
import com.trung.userdriverservice.exception.InvalidCredentialsException;
import com.trung.userdriverservice.exception.ResourceNotFoundException;

public interface AuthService {
    ApiResponse<LoginResponse> login(LoginRequest request) throws ResourceNotFoundException, InvalidCredentialsException;

    ApiResponse<LoginResponse> refresh(String oldRefreshToken, String oldAccessToken) throws InvalidCredentialsException, ResourceNotFoundException;

    ApiResponse<String> logout(String accessToken, String refreshToken);
}