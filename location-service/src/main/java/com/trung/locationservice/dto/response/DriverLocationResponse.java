package com.trung.locationservice.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class DriverLocationResponse {
    private Long driverId;
    private Double longitude;
    private Double latitude;
    private Double distanceInKm;
}