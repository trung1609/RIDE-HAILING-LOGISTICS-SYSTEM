package com.trung.bookingservice.dto.request;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PricingRequest {
    private Double startLongitude;
    private Double startLatitude;
    private Double endLongitude;
    private Double endLatitude;
}