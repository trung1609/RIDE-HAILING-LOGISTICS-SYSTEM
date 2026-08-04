package com.trung.bookingservice.service.client;

import com.trung.bookingservice.dto.response.DriverNearbyResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;

@FeignClient(name = "location-service")
public interface LocationClient {

    @GetMapping("/api/v1/internal/locations/drivers/nearby")
    List<DriverNearbyResponse> getNearbyDrivers(
            @RequestParam("longitude") Double longitude,
            @RequestParam("latitude") Double latitude,
            @RequestParam("radius") Double radius
    );
}