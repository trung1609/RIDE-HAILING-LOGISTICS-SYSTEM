package com.trung.pricingservice.controller;

import com.trung.pricingservice.dto.request.PricingConfigRequest;
import com.trung.pricingservice.dto.response.ApiResponse;
import com.trung.pricingservice.entity.PricingConfig;
import com.trung.pricingservice.service.PricingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pricing/admin")
@RequiredArgsConstructor
public class AdminPricingController {

    private final PricingService pricingService;

    @GetMapping("/config")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PricingConfig>> getPricingConfig() {
        PricingConfig config = pricingService.getBasePricingConfig();
        return ResponseEntity.ok(ApiResponse.<PricingConfig>builder()
                .success(true)
                .message("Lấy cấu hình bảng giá hiện tại thành công")
                .data(config)
                .build());
    }

    @PutMapping("/config")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PricingConfig>> updatePricingConfig(
            @Valid @RequestBody PricingConfigRequest request) {
        PricingConfig updatedConfig = pricingService.updateBasePricingConfig(request);
        return ResponseEntity.ok(ApiResponse.<PricingConfig>builder()
                .success(true)
                .message("Cập nhật giá cước thành công vào Redis!")
                .data(updatedConfig)
                .build());
    }
}