package com.trung.userdriverservice.service;

import com.trung.userdriverservice.dto.request.DriverRegisterRequest;
import com.trung.userdriverservice.dto.request.DriverUpdateRequest;
import com.trung.userdriverservice.dto.response.ApiResponse;
import com.trung.userdriverservice.dto.response.UserResponse;
import com.trung.userdriverservice.exception.BadRequestException;
import com.trung.userdriverservice.exception.ResourceConflictException;
import com.trung.userdriverservice.exception.ResourceNotFoundException;

public interface DriverService {
    ApiResponse<UserResponse> registerDriver(DriverRegisterRequest request) throws ResourceConflictException;
    ApiResponse<UserResponse> updateDriverVehicle(Long driverId, DriverUpdateRequest request) throws ResourceNotFoundException, ResourceConflictException, BadRequestException;
    void toggleDriverActiveStatus(Long driverId, boolean isActive) throws ResourceNotFoundException, BadRequestException;

    void updateDriverStatusInternal(Long driverId, boolean isOnline) throws ResourceNotFoundException;

    boolean isDriverOnline(Long driverId) throws ResourceNotFoundException;
}
