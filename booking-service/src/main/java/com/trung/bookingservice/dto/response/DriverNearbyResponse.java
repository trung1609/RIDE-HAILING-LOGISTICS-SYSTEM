package com.trung.bookingservice.dto.response;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DriverNearbyResponse {
    private Long driverId;
    private Double distanceInKm;
    private Double longitude;
    private Double latitude;
}
