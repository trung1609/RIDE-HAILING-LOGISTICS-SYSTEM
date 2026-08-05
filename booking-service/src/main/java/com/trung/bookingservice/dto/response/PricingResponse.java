package com.trung.bookingservice.dto.response;
import lombok.Data;

@Data
public class PricingResponse {
    private double totalPrice;
    private double surgeMultiplier;
    private String surgeLevel;
    private double distanceInKm;
}