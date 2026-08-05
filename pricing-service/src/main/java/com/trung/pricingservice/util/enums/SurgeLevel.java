package com.trung.pricingservice.util.enums;

import lombok.Getter;

@Getter
public enum SurgeLevel {
    NORMAL(1.0, "Bình thường - Không tăng giá"),
    LIGHT(1.2, "Cao điểm nhẹ - Nhu cầu tăng nhẹ"),
    MODERATE(1.5, "Cao điểm trung bình - Thiếu tài xế khu vực"),
    SEVERE(2.0, "Cao điểm nghiêm trọng - Thời tiết xấu/Kẹt xe nặng");

    private final double multiplier;
    private final String description;

    SurgeLevel(double multiplier, String description) {
        this.multiplier = multiplier;
        this.description = description;
    }
}