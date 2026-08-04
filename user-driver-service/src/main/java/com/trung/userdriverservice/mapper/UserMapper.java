package com.trung.userdriverservice.mapper;

import com.trung.userdriverservice.dto.request.DriverRegisterRequest;
import com.trung.userdriverservice.dto.request.UserRegisterRequest;
import com.trung.userdriverservice.dto.response.DriverInternalResponse;
import com.trung.userdriverservice.dto.response.UserPaymentInfoResponse;
import com.trung.userdriverservice.dto.response.UserResponse;
import com.trung.userdriverservice.entity.DriverProfile;
import com.trung.userdriverservice.entity.User;
import com.trung.userdriverservice.util.enums.DriverStatus;
import com.trung.userdriverservice.util.enums.Role;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    private final PasswordEncoder passwordEncoder;

    public UserMapper(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    public User toCustomerEntity(UserRegisterRequest request) {
        User user = new User();
        user.setPhoneNumber(request.getPhoneNumber());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setRole(Role.CUSTOMER);
        return user;
    }

    public User toDriverEntity(DriverRegisterRequest request) {
        User user = new User();
        user.setPhoneNumber(request.getPhoneNumber());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setRole(Role.DRIVER);
        return user;
    }

    public DriverProfile toDriverProfileEntity(DriverRegisterRequest request, User user) {
        DriverProfile profile = new DriverProfile();
        profile.setUser(user);
        profile.setVehicleType(request.getVehicleType());
        profile.setLicensePlate(request.getLicensePlate());
        profile.setVehicleModel(request.getVehicleModel());
        profile.setStatus(DriverStatus.OFFLINE);
        return profile;
    }

    public UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .phoneNumber(user.getPhoneNumber())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .build();
    }

    public DriverInternalResponse toDriverInternalResponse(DriverProfile profile) {
        return DriverInternalResponse.builder()
                .driverId(profile.getDriverId())
                .fullName(profile.getUser().getFullName())
                .phoneNumber(profile.getUser().getPhoneNumber())
                .vehicleType(profile.getVehicleType())
                .licensePlate(profile.getLicensePlate())
                .vehicleModel(profile.getVehicleModel())
                .status(profile.getStatus())
                .build();
    }

    public UserPaymentInfoResponse toUserPaymentInfoResponse(User user) {
        return UserPaymentInfoResponse.builder()
                .userId(user.getId())
                .fullName(user.getFullName())
                .phoneNumber(user.getPhoneNumber())
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }
}
