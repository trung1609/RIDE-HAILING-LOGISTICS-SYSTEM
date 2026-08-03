package com.trung.userdriverservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DriverRegisterRequest extends UserRegisterRequest {

    @NotBlank(message = "Loại xe không được để trống (VD: BIKE, CAR_4_SEATS, MOTORBIKE,...)")
    private String vehicleType;

    @NotBlank(message = "Biển số xe không được để trống")
    private String licensePlate;

    @NotBlank(message = "Dòng xe không được để trống (VD: Honda Wave, Toyota Vios, ...)")
    private String vehicleModel;
}
