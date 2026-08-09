package com.trung.pricingservice.service;

import com.trung.pricingservice.dto.request.PricingConfigRequest;
import com.trung.pricingservice.dto.request.PricingRequest;
import com.trung.pricingservice.dto.response.PricingResponse;
import com.trung.pricingservice.entity.PricingConfig;

public interface PricingService {
    PricingResponse calculateDynamicPrice(PricingRequest request, int nearbyDriversCount);
    PricingConfig updateBasePricingConfig(PricingConfigRequest request);
    PricingConfig getBasePricingConfig();
}