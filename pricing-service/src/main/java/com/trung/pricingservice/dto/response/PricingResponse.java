package com.trung.pricingservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingResponse {
    private double baseFare;
    private double distanceFare;
    private double durationFare;
    private double surgeMultiplier;
    private String surgeLevel;
    private double totalPrice;
    private double distanceInKm;
    private int estimatedDurationMinutes;
    private String h3Index;
}