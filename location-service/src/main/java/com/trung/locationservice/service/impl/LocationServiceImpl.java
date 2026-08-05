package com.trung.locationservice.service.impl;

import com.trung.locationservice.dto.request.LocationUpdateRequest;
import com.trung.locationservice.dto.response.DriverLocationResponse;
import com.trung.locationservice.service.LocationService;
import com.trung.locationservice.service.client.UserDriverClient;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LocationServiceImpl implements LocationService {

    private static final Logger log = LoggerFactory.getLogger(LocationServiceImpl.class);
    private final StringRedisTemplate redisTemplate;

    private static final String DRIVER_LOCATION_KEY = "drivers:online:locations";
    private final UserDriverClient userDriverClient;

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

        if (results == null) return nearbyDrivers;

        // Lọc driver chưa bị reserve trước, thu thập ID để batch check
        List<Long> candidateIds = new ArrayList<>();
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> candidateResults = new ArrayList<>();

        for (GeoResult<RedisGeoCommands.GeoLocation<String>> result : results) {
            Long driverId = Long.parseLong(result.getContent().getName());
            Boolean isReserved = redisTemplate.hasKey("drivers:reserved:" + driverId);
            if (Boolean.TRUE.equals(isReserved)) {
                log.info("Tài xế {} đang được giữ chỗ cho cuốc xe khác, bỏ qua!", driverId);
                continue;
            }
            candidateIds.add(driverId);
            candidateResults.add(result);
        }

        if (candidateIds.isEmpty()) return nearbyDrivers;

        // Một lần gọi Feign duy nhất thay vì N lần
        Map<Long, Boolean> onlineStatusMap;
        try {
            onlineStatusMap = userDriverClient.getBatchDriversOnlineStatus(candidateIds);
        } catch (Exception e) {
            log.error("Không thể kiểm tra trạng thái trực tuyến hàng loạt tài xế: {}", e.getMessage());
            return nearbyDrivers;
        }

        for (GeoResult<RedisGeoCommands.GeoLocation<String>> result : candidateResults) {
            Long driverId = Long.parseLong(result.getContent().getName());
            if (Boolean.TRUE.equals(onlineStatusMap.get(driverId))) {
                nearbyDrivers.add(DriverLocationResponse.builder()
                        .driverId(driverId)
                        .latitude(result.getContent().getPoint().getY())
                        .longitude(result.getContent().getPoint().getX())
                        .distanceInKm(result.getDistance().getValue())
                        .build());
            }
        }

        nearbyDrivers.sort(Comparator.comparingDouble(DriverLocationResponse::getDistanceInKm));
        return nearbyDrivers;
    }
}