package com.trung.locationservice.controller;

import com.trung.locationservice.dto.request.LocationUpdateRequest;
import com.trung.locationservice.dto.response.DriverLocationResponse;
import com.trung.locationservice.service.LocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    @PostMapping("/drivers/me")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<String> updateMyLocation(
            @RequestHeader("X-User-Id") Long driverId,
            @Valid @RequestBody LocationUpdateRequest request) {

        locationService.updateDriverLocation(driverId, request);
        return ResponseEntity.ok("Đã cập nhật vị trí");
    }
}