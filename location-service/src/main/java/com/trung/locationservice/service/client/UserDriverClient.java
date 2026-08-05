package com.trung.locationservice.service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(name = "user-driver-service")
public interface UserDriverClient {
    @PutMapping("/api/v1/internal/drivers/{driverId}/status/toggle")
    void setDriverStatusInternal(@PathVariable("driverId") Long driverId, @RequestParam("isOnline") boolean isOnline);

    @PostMapping("/api/v1/internal/drivers/batch/online-status")
    Map<Long, Boolean> getBatchDriversOnlineStatus(@RequestBody List<Long> driverIds);
}