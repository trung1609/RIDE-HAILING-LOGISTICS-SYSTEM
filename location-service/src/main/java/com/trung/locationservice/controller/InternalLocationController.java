package com.trung.locationservice.controller;

import com.trung.locationservice.service.LocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}