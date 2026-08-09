package com.trung.pricingservice.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingConfigRequest {

    @NotNull(message = "Giá mở cửa không được để trống")
    @Min(value = 0, message = "Giá mở cửa phải lớn hơn hoặc bằng 0")
    private Double baseFare;

    @NotNull(message = "Giá mỗi km không được để trống")
    @Min(value = 0, message = "Giá mỗi km phải lớn hơn hoặc bằng 0")
    private Double pricePerKm;

    @NotNull(message = "Giá mỗi phút không được để trống")
    @Min(value = 0, message = "Giá mỗi phút phải lớn hơn hoặc bằng 0")
    private Double pricePerMinute;
}