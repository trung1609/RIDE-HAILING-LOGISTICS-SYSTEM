package com.trung.locationservice.controller;

import com.trung.locationservice.dto.request.LocationUpdateRequest;
import com.trung.locationservice.service.LocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
@Slf4j
public class LocationWebSocketController {

    private final LocationService locationService;

    @MessageMapping("/driver/location")
    public void updateLocation(
            @Payload LocationUpdateRequest request,
            @Header("driverId") Long driverId) {

        log.info("Nội bộ WS: Nhận tọa độ từ driver {}: [{}, {}]", driverId, request.getLongitude(), request.getLatitude());
        locationService.updateDriverLocation(driverId, request);
    }
}