package com.trung.bookingservice.util;

public class LocationUtils {
    private static final double EARTH_RADIUS_KM = 6371.0; // Bán kính Trái Đất

    public static double calculateDistance(double startLat, double startLng, double endLat, double endLng) {
        double latDistance = Math.toRadians(endLat - startLat);
        double lngDistance = Math.toRadians(endLng - startLng);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(startLat)) * Math.cos(Math.toRadians(endLat))
                * Math.sin(lngDistance / 2) * Math.sin(lngDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c; // Trả về số km
    }
}