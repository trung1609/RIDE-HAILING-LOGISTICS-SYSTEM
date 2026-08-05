package com.trung.pricingservice.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingRequest {
    @NotNull(message = "Kinh độ điểm đón không được để trống")
    private Double startLongitude;

    @NotNull(message = "Vĩ độ điểm đón không được để trống")
    private Double startLatitude;

    @NotNull(message = "Kinh độ điểm đến không được để trống")
    private Double endLongitude;

    @NotNull(message = "Vĩ độ điểm đến không được để trống")
    private Double endLatitude;
}