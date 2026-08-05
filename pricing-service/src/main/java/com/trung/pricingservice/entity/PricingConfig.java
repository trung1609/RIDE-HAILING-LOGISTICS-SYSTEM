package com.trung.pricingservice.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    private double baseFare;
    private double pricePerKm;
    private double pricePerMinute;
}