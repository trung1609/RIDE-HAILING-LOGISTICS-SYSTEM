package com.trung.userdriverservice.service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "location-service")
public interface LocationClient {

    @DeleteMapping("/api/v1/internal/locations/drivers/{driverId}")
    void removeDriverLocationInternal(@PathVariable("driverId") Long driverId);
}