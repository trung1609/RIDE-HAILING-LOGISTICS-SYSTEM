package com.trung.userdriverservice.service;

import com.trung.userdriverservice.dto.response.ApiResponse;
import com.trung.userdriverservice.dto.response.DriverInternalResponse;
import com.trung.userdriverservice.dto.response.UserPaymentInfoResponse;
import com.trung.userdriverservice.exception.ResourceNotFoundException;
import com.trung.userdriverservice.util.enums.DriverStatus;

public interface InternalUserDriverService {
    ApiResponse<DriverInternalResponse> getDriverProfileInternal(Long id) throws ResourceNotFoundException;
    ApiResponse<String> updateDriverStatusInternal(Long id, DriverStatus status) throws ResourceNotFoundException;
    ApiResponse<UserPaymentInfoResponse> getUserPaymentInfoInternal(Long id) throws ResourceNotFoundException;
}