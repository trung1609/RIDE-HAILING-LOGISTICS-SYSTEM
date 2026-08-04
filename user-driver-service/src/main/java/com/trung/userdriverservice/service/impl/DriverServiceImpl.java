package com.trung.userdriverservice.service.impl;

import com.trung.userdriverservice.dto.request.DriverRegisterRequest;
import com.trung.userdriverservice.dto.request.DriverUpdateRequest;
import com.trung.userdriverservice.dto.response.ApiResponse;
import com.trung.userdriverservice.dto.response.UserResponse;
import com.trung.userdriverservice.entity.DriverProfile;
import com.trung.userdriverservice.entity.User;
import com.trung.userdriverservice.exception.BadRequestException;
import com.trung.userdriverservice.exception.ResourceConflictException;
import com.trung.userdriverservice.exception.ResourceNotFoundException;
import com.trung.userdriverservice.mapper.UserMapper;
import com.trung.userdriverservice.repository.DriverProfileRepository;
import com.trung.userdriverservice.repository.UserRepository;
import com.trung.userdriverservice.service.DriverService;
import com.trung.userdriverservice.util.enums.DriverStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DriverServiceImpl implements DriverService {
    private final UserRepository userRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public ApiResponse<UserResponse> registerDriver(DriverRegisterRequest request) throws ResourceConflictException {
        if (userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new ResourceConflictException("Số điện thoại đã được đăng ký.");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ResourceConflictException("Email đã được đăng ký.");
        }

        if (driverProfileRepository.existsByLicensePlate(request.getLicensePlate())) {
            throw new ResourceConflictException("Biển số xe này đã được đăng ký trên hệ thống.");
        }

        User user = userMapper.toDriverEntity(request);
        User savedUser = userRepository.save(user);

        DriverProfile driverProfile = userMapper.toDriverProfileEntity(request, savedUser);
        driverProfileRepository.save(driverProfile);

        return ApiResponse.<UserResponse>builder()
                .data(userMapper.toUserResponse(savedUser))
                .build();
    }

    @Override
    public ApiResponse<UserResponse> updateDriverVehicle(Long driverId, DriverUpdateRequest request) throws ResourceNotFoundException, ResourceConflictException, BadRequestException {
        DriverProfile profile = driverProfileRepository.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ tài xế."));

        if (profile.getStatus() == DriverStatus.BUSY) {
            throw new BadRequestException("Không thể cập nhật thông tin xe khi đang trong chuyến đi.");
        }

        if (!profile.getLicensePlate().equals(request.getLicensePlate()) &&
                driverProfileRepository.existsByLicensePlate(request.getLicensePlate())) {
            throw new ResourceConflictException("Biển số xe này đã được đăng ký bởi người khác.");
        }

        profile.setLicensePlate(request.getLicensePlate());
        profile.setVehicleModel(request.getVehicleModel());
        profile.setVehicleType(request.getVehicleType());
        driverProfileRepository.save(profile);

        return ApiResponse.<UserResponse>builder()
                .data(userMapper.toUserResponse(profile.getUser()))
                .build();
    }

    @Override
    public void toggleDriverActiveStatus(Long driverId, boolean isActive) throws ResourceNotFoundException, BadRequestException {
        DriverProfile profile = driverProfileRepository.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ tài xế."));

        if (profile.getStatus() == DriverStatus.BUSY) {
            throw new BadRequestException("Không thể thay đổi trạng thái khi tài xế đang trong chuyến đi.");
        }

        profile.setStatus(isActive ? DriverStatus.ONLINE : DriverStatus.OFFLINE);
        driverProfileRepository.save(profile);
    }
}
