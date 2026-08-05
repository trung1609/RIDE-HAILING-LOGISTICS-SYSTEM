package com.trung.pricingservice.service;

import com.trung.pricingservice.dto.request.PricingRequest;
import com.trung.pricingservice.dto.response.PricingResponse;

public interface PricingService {
    PricingResponse calculateDynamicPrice(PricingRequest request, int nearbyDriversCount);
}