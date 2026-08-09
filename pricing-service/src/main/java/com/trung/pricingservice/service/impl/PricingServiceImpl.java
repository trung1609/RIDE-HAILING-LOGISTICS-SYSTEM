package com.trung.pricingservice.service.impl;

import com.trung.pricingservice.dto.request.PricingConfigRequest;
import com.trung.pricingservice.dto.request.PricingRequest;
import com.trung.pricingservice.dto.response.PricingResponse;
import com.trung.pricingservice.entity.PricingConfig;
import com.trung.pricingservice.mapper.PricingMapper;
import com.trung.pricingservice.repository.PricingConfigRepository;
import com.trung.pricingservice.service.PricingService;
import com.trung.pricingservice.util.enums.SurgeLevel;
import com.uber.h3core.H3Core;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PricingServiceImpl implements PricingService {

    private final PricingConfigRepository configRepository;
    private final PricingMapper pricingMapper;
    private final H3Core h3Core;

    private static final int H3_RESOLUTION = 8; // Bán kính ô lục giác khoảng 700 mét
    private static final double EARTH_RADIUS_KM = 6371.0;

    @Override
    public PricingResponse calculateDynamicPrice(PricingRequest request, int nearbyDriversCount) {
        // Phân vùng điểm đón khách hàng vào ô lục giác H3
        String h3Index = h3Core.latLngToCellAddress(request.getStartLatitude(), request.getStartLongitude(), H3_RESOLUTION);

        // Ghi nhận thêm 1 lượt Cầu (Demand) mới vào ô lục giác này
        configRepository.updateDemand(h3Index);
        int totalDemand = configRepository.getDemand(h3Index);

        // Tính toán khoảng cách & thời gian ước tính hình học (Haversine formula)
        double distance = calculateHaversineDistance(
                request.getStartLatitude(), request.getStartLongitude(),
                request.getEndLatitude(), request.getEndLongitude()
        );
        // Giả lập tốc độ di chuyển trung bình đô thị 30km/h
        int estimatedDuration = (int) Math.ceil((distance / 30.0) * 60.0);

        // Lấy cấu hình bảng giá gốc từ Redis
        PricingConfig baseConfig = configRepository.getBasePricingConfig();

        SurgeLevel surgeLevel = determineSurgeLevel(totalDemand, nearbyDriversCount);

        // Tính toán chi phí phân tầng
        double distanceFare = distance * baseConfig.getPricePerKm();
        double durationFare = estimatedDuration * baseConfig.getPricePerMinute();

        double rawTotal = baseConfig.getBaseFare() + distanceFare + durationFare;
        double finalPrice = Math.round((rawTotal * surgeLevel.getMultiplier()) / 1000.0) * 1000.0;

        log.info("[H3-Surge] Cell: {} | Cầu: {} | Cung: {} -> Mức: {} ({}x)",
                h3Index, totalDemand, nearbyDriversCount, surgeLevel.name(), surgeLevel.getMultiplier());

        return pricingMapper.toPricingResponse(
                baseConfig, distanceFare, durationFare, surgeLevel, finalPrice, distance, estimatedDuration, h3Index
        );
    }

    @Override
    public PricingConfig updateBasePricingConfig(PricingConfigRequest request) {
        PricingConfig newConfig = PricingConfig.builder()
                .baseFare(request.getBaseFare())
                .pricePerKm(request.getPricePerKm())
                .pricePerMinute(request.getPricePerMinute())
                .build();

        configRepository.updatePricingConfig(newConfig);
        log.info("Đã cập nhật cấu hình bảng giá mới vào Redis: {}", newConfig);
        return newConfig;
    }

    @Override
    public PricingConfig getBasePricingConfig() {
        return configRepository.getBasePricingConfig();
    }

    private SurgeLevel determineSurgeLevel(int demand, int supply) {
        if (supply == 0 && demand > 0) return SurgeLevel.SEVERE;

        double ratio = (double) demand / Math.max(supply, 1);

        if (ratio > 3.0) return SurgeLevel.SEVERE;
        if (ratio > 1.8) return SurgeLevel.MODERATE;
        if (ratio > 1.2) return SurgeLevel.LIGHT;
        return SurgeLevel.NORMAL;
    }

    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }
}