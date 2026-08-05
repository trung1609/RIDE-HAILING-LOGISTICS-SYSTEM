package com.trung.userdriverservice.controller;

import com.trung.userdriverservice.dto.request.DriverRegisterRequest;
import com.trung.userdriverservice.dto.request.DriverUpdateRequest;
import com.trung.userdriverservice.dto.response.ApiResponse;
import com.trung.userdriverservice.dto.response.UserResponse;
import com.trung.userdriverservice.exception.BadRequestException;
import com.trung.userdriverservice.exception.ResourceConflictException;
import com.trung.userdriverservice.exception.ResourceNotFoundException;
import com.trung.userdriverservice.service.DriverService;
import com.trung.userdriverservice.util.enums.DriverStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/drivers")
@RequiredArgsConstructor
public class DriverController {

    private final DriverService driverService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> registerDriver(@Valid @RequestBody DriverRegisterRequest request) throws ResourceConflictException {
        ApiResponse<UserResponse> response = ApiResponse.<UserResponse>builder()
                .success(true)
                .message("Đăng ký tài khoản tài xế thành công")
                .data(driverService.registerDriver(request).getData())
                .timestamp(LocalDateTime.now())
                .build();

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/{driverId}/status")
    @PreAuthorize("hasRole('DRIVER') or (hasRole('DRIVER') and #currentDriverId == #driverId)")
    public ResponseEntity<ApiResponse<String>> toggleDriverActiveStatus(@PathVariable Long driverId,
                                                                        @RequestParam boolean isActive,
                                                                        @RequestHeader(name = "X-User-Id") Long currentDriverId) throws ResourceNotFoundException, BadRequestException {
        driverService.toggleDriverActiveStatus(driverId, isActive);

        ApiResponse<String> response = ApiResponse.<String>builder()
                .success(true)
                .message("Cập nhật trạng thái tài xế thành công")
                .timestamp(LocalDateTime.now())
                .build();

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PutMapping("/{driverId}/vehicle")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('DRIVER') and #currentDriverId == #driverId)")
    public ResponseEntity<ApiResponse<UserResponse>> updateDriverVehicle(@PathVariable Long driverId,
                                                                         @Valid @RequestBody DriverUpdateRequest request,
                                                                         @RequestHeader(name = "X-User-Id") Long currentDriverId) throws ResourceNotFoundException, ResourceConflictException, BadRequestException {
        ApiResponse<UserResponse> response = driverService.updateDriverVehicle(driverId, request);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
