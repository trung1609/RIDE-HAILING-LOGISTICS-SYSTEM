package com.trung.userdriverservice.dto.request;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DriverUpdateRequest {
    private String vehicleType;
    private String licensePlate;
    private String vehicleModel;
}
