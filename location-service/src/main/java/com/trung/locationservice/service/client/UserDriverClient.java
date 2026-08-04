package com.trung.locationservice.service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "user-driver-service")
public interface UserDriverClient {
    @PutMapping("/api/v1/internal/drivers/{driverId}/status/toggle")
    void setDriverStatusInternal(@PathVariable("driverId") Long driverId, @RequestParam("isOnline") boolean isOnline);

    @GetMapping("/api/v1/internal/drivers/{driverId}/is-online")
    boolean isDriverOnline(@PathVariable("driverId") Long driverId);
}