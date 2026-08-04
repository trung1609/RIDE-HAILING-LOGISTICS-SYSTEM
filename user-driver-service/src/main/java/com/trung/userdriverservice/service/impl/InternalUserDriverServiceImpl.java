package com.trung.userdriverservice.service.impl;

import com.trung.userdriverservice.dto.response.ApiResponse;
import com.trung.userdriverservice.dto.response.DriverInternalResponse;
import com.trung.userdriverservice.dto.response.UserPaymentInfoResponse;
import com.trung.userdriverservice.entity.DriverProfile;
import com.trung.userdriverservice.entity.User;
import com.trung.userdriverservice.exception.ResourceNotFoundException;
import com.trung.userdriverservice.mapper.UserMapper;
import com.trung.userdriverservice.repository.DriverProfileRepository;
import com.trung.userdriverservice.repository.UserRepository;
import com.trung.userdriverservice.service.InternalUserDriverService;
import com.trung.userdriverservice.util.enums.DriverStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class InternalUserDriverServiceImpl implements InternalUserDriverService {

    private final UserRepository userRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final UserMapper userMapper;

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<DriverInternalResponse> getDriverProfileInternal(Long id) throws ResourceNotFoundException {
        DriverProfile profile = driverProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ tài xế với ID: " + id));

        return ApiResponse.<DriverInternalResponse>builder()
                .success(true)
                .message("Lấy thông tin tài xế nội bộ thành công")
                .data(userMapper.toDriverInternalResponse(profile))
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Override
    @Transactional
    public ApiResponse<String> updateDriverStatusInternal(Long id, DriverStatus status) throws ResourceNotFoundException {
        DriverProfile profile = driverProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ tài xế với ID: " + id));

        profile.setStatus(status);
        driverProfileRepository.save(profile);

        return ApiResponse.<String>builder()
                .success(true)
                .message("Cập nhật trạng thái tài xế thành công: " + status)
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<UserPaymentInfoResponse> getUserPaymentInfoInternal(Long id) throws ResourceNotFoundException {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với ID: " + id));

        return ApiResponse.<UserPaymentInfoResponse>builder()
                .success(true)
                .message("Lấy thông tin thanh toán người dùng thành công")
                .data(userMapper.toUserPaymentInfoResponse(user))
                .timestamp(LocalDateTime.now())
                .build();
    }
}