package com.trung.locationservice.service;

import com.trung.locationservice.dto.request.LocationUpdateRequest;
import com.trung.locationservice.dto.response.DriverLocationResponse;

import java.util.List;

public interface LocationService {
    void updateDriverLocation(Long driverId, LocationUpdateRequest request);
    void removeDriverLocation(Long driverId);
    List<DriverLocationResponse> getNearbyDrivers(Double longitude, Double latitude, Double radiusInKm);
}
