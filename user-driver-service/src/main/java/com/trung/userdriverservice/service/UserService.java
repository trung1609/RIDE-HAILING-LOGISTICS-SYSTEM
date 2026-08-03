package com.trung.userdriverservice.service;

import com.trung.userdriverservice.dto.request.PageRequestDTO;
import com.trung.userdriverservice.dto.request.UserRegisterRequest;
import com.trung.userdriverservice.dto.response.ApiResponse;
import com.trung.userdriverservice.dto.response.PageResponseDTO;
import com.trung.userdriverservice.dto.response.UserResponse;
import com.trung.userdriverservice.exception.ResourceConflictException;
import com.trung.userdriverservice.exception.ResourceNotFoundException;

public interface UserService {
    ApiResponse<UserResponse> registerCustomer(UserRegisterRequest request) throws ResourceConflictException;
    PageResponseDTO<UserResponse> getAllUsers(PageRequestDTO pageRequestDTO);
    ApiResponse<UserResponse> getUserById(Long id) throws ResourceNotFoundException;
    void lockUserAccount(Long userId) throws ResourceNotFoundException;
}
