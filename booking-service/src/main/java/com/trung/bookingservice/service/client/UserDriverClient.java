package com.trung.bookingservice.service.client;

import com.trung.bookingservice.dto.response.ApiResponse;
import com.trung.bookingservice.util.enums.DriverStatus;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "user-driver-service")
public interface UserDriverClient {

    @PutMapping("/api/v1/internal/drivers/{id}/status")
    ResponseEntity<ApiResponse<String>> updateDriverStatusInternal(
            @PathVariable Long id,
            @RequestParam DriverStatus status);
}
