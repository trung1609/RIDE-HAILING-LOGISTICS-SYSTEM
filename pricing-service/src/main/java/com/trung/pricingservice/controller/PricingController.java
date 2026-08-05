package com.trung.pricingservice.controller;

import com.trung.pricingservice.dto.request.PricingRequest;
import com.trung.pricingservice.dto.response.PricingResponse;
import com.trung.pricingservice.service.PricingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pricing")
@RequiredArgsConstructor
public class PricingController {

    private final PricingService pricingService;

    @PostMapping("/calculate")
    public ResponseEntity<PricingResponse> calculatePrice(
            @Valid @RequestBody PricingRequest request,
            @RequestParam(value = "nearbyDriversCount", defaultValue = "0") int nearbyDriversCount) {

        PricingResponse response = pricingService.calculateDynamicPrice(request, nearbyDriversCount);
        return ResponseEntity.ok(response);
    }
}