package com.trung.userdriverservice.dto.response;

import com.trung.userdriverservice.util.enums.DriverStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class DriverInternalResponse {
    private Long driverId;
    private String fullName;
    private String phoneNumber;
    private String vehicleType;
    private String licensePlate;
    private String vehicleModel;
    private DriverStatus status;
}