package com.trung.locationservice.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LocationUpdateRequest {
    @NotNull(message = "Kinh độ không được để trống")
    private Double longitude;

    @NotNull(message = "Vĩ độ không được để trống")
    private Double latitude;
}