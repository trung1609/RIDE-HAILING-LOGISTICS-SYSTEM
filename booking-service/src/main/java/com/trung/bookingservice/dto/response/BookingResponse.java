package com.trung.bookingservice.dto.response;

import com.trung.bookingservice.util.enums.BookingStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BookingResponse {
    private Long bookingId;
    private Long customerId;
    private Long driverId;
    private Double startLongitude;
    private Double startLatitude;
    private Double endLongitude;
    private Double endLatitude;
    private BookingStatus status;
    private Double distanceInKm;
    private Double price;
}