package com.trung.locationservice.controller;

import com.trung.locationservice.dto.response.DriverLocationResponse;
import com.trung.locationservice.service.LocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/internal/locations")
@RequiredArgsConstructor
public class InternalLocationController {

    private final LocationService locationService;

    @DeleteMapping("/drivers/{driverId}")
    public ResponseEntity<Void> removeDriverLocationInternal(@PathVariable Long driverId) {
        locationService.removeDriverLocation(driverId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/drivers/nearby")
    public ResponseEntity<List<DriverLocationResponse>> getNearbyDrivers(
            @RequestParam Double longitude,
            @RequestParam Double latitude,
            @RequestParam(defaultValue = "5.0") Double radius) {

        List<DriverLocationResponse> drivers = locationService.getNearbyDrivers(longitude, latitude, radius);
        return ResponseEntity.ok(drivers);
    }
}