package com.trung.userdriverservice.controller;

import com.trung.userdriverservice.dto.response.ApiResponse;
import com.trung.userdriverservice.dto.response.DriverInternalResponse;
import com.trung.userdriverservice.dto.response.UserPaymentInfoResponse;
import com.trung.userdriverservice.exception.ResourceNotFoundException;
import com.trung.userdriverservice.service.DriverService;
import com.trung.userdriverservice.service.InternalUserDriverService;
import com.trung.userdriverservice.util.enums.DriverStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/internal")
@RequiredArgsConstructor
public class InternalUserDriverController {

    private final InternalUserDriverService internalUserDriverService;
    private final DriverService driverService;

    @GetMapping("/drivers/{id}")
    public ResponseEntity<ApiResponse<DriverInternalResponse>> getDriverProfileInternal(@PathVariable Long id) throws ResourceNotFoundException {
        return ResponseEntity.ok(internalUserDriverService.getDriverProfileInternal(id));
    }

    @PutMapping("/drivers/{id}/status")
    public ResponseEntity<ApiResponse<String>> updateDriverStatusInternal(
            @PathVariable Long id,
            @RequestParam DriverStatus status) throws ResourceNotFoundException {
        return ResponseEntity.ok(internalUserDriverService.updateDriverStatusInternal(id, status));
    }

    @GetMapping("/users/{id}/payment-info")
    public ResponseEntity<ApiResponse<UserPaymentInfoResponse>> getUserPaymentInfoInternal(@PathVariable Long id) throws ResourceNotFoundException {
        return ResponseEntity.ok(internalUserDriverService.getUserPaymentInfoInternal(id));
    }

    @PutMapping("/drivers/{driverId}/status/toggle")
    public ResponseEntity<Void> setDriverStatusInternal(
            @PathVariable Long driverId,
            @RequestParam boolean isOnline) throws ResourceNotFoundException {

        driverService.updateDriverStatusInternal(driverId, isOnline);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/drivers/{driverId}/is-online")
    public ResponseEntity<Boolean> isDriverOnlineInternal(@PathVariable Long driverId) throws ResourceNotFoundException {
        boolean online = driverService.isDriverOnline(driverId);
        return ResponseEntity.ok(online);
    }
}