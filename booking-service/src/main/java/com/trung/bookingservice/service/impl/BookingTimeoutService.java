package com.trung.bookingservice.service.impl;

import com.trung.bookingservice.dto.response.BookingResponse;
import com.trung.bookingservice.entity.Booking;
import com.trung.bookingservice.repository.BookingRepository;
import com.trung.bookingservice.util.LocationUtils;
import com.trung.bookingservice.util.enums.BookingStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingTimeoutService {

    private final BookingRepository bookingRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final StringRedisTemplate redisTemplate;

    @Async
    @Transactional
    public void checkTimeoutAndCancel(Long bookingId, Long invitedDriverId) {
        try {
            Thread.sleep(20000);

            Booking booking = bookingRepository.findById(bookingId).orElse(null);

            if (booking != null && booking.getStatus() == BookingStatus.PENDING) {
                booking.setStatus(BookingStatus.CANCELLED);
                bookingRepository.save(booking);

                log.warn("Cuốc xe #{} đã quá thời gian chờ (20s) và tự động bị hủy (CANCELLED).", bookingId);

                redisTemplate.delete("drivers:reserved:" + invitedDriverId);

                double distance = LocationUtils.calculateDistance(
                        booking.getStartLatitude(), booking.getStartLongitude(),
                        booking.getEndLatitude(), booking.getEndLongitude()
                );

                BookingResponse cancelResponse = BookingResponse.builder()
                        .bookingId(booking.getId())
                        .customerId(booking.getCustomerId())
                        .driverId(invitedDriverId)
                        .startLongitude(booking.getStartLongitude())
                        .startLatitude(booking.getStartLatitude())
                        .endLongitude(booking.getEndLongitude())
                        .endLatitude(booking.getEndLatitude())
                        .status(booking.getStatus())
                        .distanceInKm(distance)
                        .price(booking.getPrice())
                        .build();

                messagingTemplate.convertAndSendToUser(
                        booking.getCustomerId().toString(),
                        "/queue/booking/status",
                        cancelResponse
                );

                messagingTemplate.convertAndSendToUser(
                        invitedDriverId.toString(),
                        "/queue/driver/match",
                        cancelResponse
                );
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Lỗi tiến trình chờ hủy cuốc xe: ", e);
        }
    }
}