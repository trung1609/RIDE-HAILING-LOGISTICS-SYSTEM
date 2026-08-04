package com.trung.locationservice.service.impl;

import com.trung.locationservice.dto.request.LocationUpdateRequest;
import com.trung.locationservice.dto.response.DriverLocationResponse;
import com.trung.locationservice.service.LocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LocationServiceImpl implements LocationService {

    private final StringRedisTemplate redisTemplate;

    private static final String DRIVER_LOCATION_KEY = "drivers:online:locations";

    @Override
    public void updateDriverLocation(Long driverId, LocationUpdateRequest request) {
        redisTemplate.opsForGeo().add(
                DRIVER_LOCATION_KEY,
                new Point(request.getLongitude(), request.getLatitude()),
                String.valueOf(driverId)
        );
    }

    @Override
    public void removeDriverLocation(Long driverId) {
        redisTemplate.opsForGeo().remove(
                DRIVER_LOCATION_KEY,
                String.valueOf(driverId)
        );
    }

    @Override
    public List<DriverLocationResponse> getNearbyDrivers(Double longitude, Double latitude, Double radiusInKm) {
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redisTemplate.opsForGeo().search(
                DRIVER_LOCATION_KEY,
                GeoReference.fromCoordinate(new Point(longitude, latitude)),
                new Distance(radiusInKm, Metrics.KILOMETERS),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                        .includeDistance()
                        .includeCoordinates()
                        .sortAscending()
        );

        List<DriverLocationResponse> nearbyDrivers = new ArrayList<>();

        if (results != null) {
            for (GeoResult<RedisGeoCommands.GeoLocation<String>> result : results) {
                Point point = result.getContent().getPoint();
                nearbyDrivers.add(DriverLocationResponse.builder()
                        .driverId(Long.parseLong(result.getContent().getName()))
                        .distanceInKm(result.getDistance().getValue())
                        .longitude(point != null ? point.getX() : null)
                        .latitude(point != null ? point.getY() : null)
                        .build());
            }
        }
        return nearbyDrivers;
    }
}