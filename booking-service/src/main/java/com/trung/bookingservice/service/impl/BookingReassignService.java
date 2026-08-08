package com.trung.bookingservice.service.impl;

import com.trung.bookingservice.dto.response.BookingResponse;
import com.trung.bookingservice.dto.response.DriverNearbyResponse;
import com.trung.bookingservice.entity.Booking;
import com.trung.bookingservice.repository.BookingRepository;
import com.trung.bookingservice.service.client.LocationClient;
import com.trung.bookingservice.service.client.UserDriverClient;
import com.trung.bookingservice.util.enums.BookingStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingReassignService {

    private final LocationClient locationClient;
    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final BookingRepository bookingRepository;
    private final UserDriverClient userDriverClient;

    @Autowired
    @Lazy
    private BookingTimeoutService bookingTimeoutService;

    @Async("taskExecutor")
    @Transactional
    public void reassignNewDriverAsync(Long bookingId, Double startLng, Double startLat, double distanceInKm) {
        log.info("Kích hoạt tiến trình ngầm tự động tìm tài xế thay thế cho cuốc xe #{}", bookingId);

        // Đợi 2 giây nhẹ để các hệ thống đồng bộ trạng thái tài xế cũ vừa hủy
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Kiểm tra trạng thái booking MỘT LẦN trước khi search
        Booking currentBooking = bookingRepository.findById(bookingId).orElse(null);
        if (currentBooking == null || currentBooking.getStatus() != BookingStatus.PENDING) {
            log.info("Cuốc xe #{} không còn ở trạng thái PENDING hoặc đã bị khách hủy, dừng reassign.", bookingId);
            return;
        }

        // Gọi location service MỘT LẦN với bán kính lớn nhất
        List<DriverNearbyResponse> allNearbyDrivers = locationClient.getNearbyDrivers(startLng, startLat, 8.0);

        double[] searchRadiuses = {3.0, 5.0, 8.0};
        String rejectedKey = "booking:rejected:" + bookingId;

        for (double radius : searchRadiuses) {
            // Filter in-memory theo từng nấc bán kính
            List<DriverNearbyResponse> nearbyDrivers = allNearbyDrivers.stream()
                    .filter(d -> d.getDistanceInKm() <= radius)
                    .toList();

            for (DriverNearbyResponse driver : nearbyDrivers) {
                Long driverId = driver.getDriverId();

                Boolean isRejected = redisTemplate.opsForSet().isMember(rejectedKey, driverId.toString());
                if (Boolean.TRUE.equals(isRejected)) {
                    log.info("Bỏ qua tài xế {} vì đã từng hủy cuốc #{}", driverId, bookingId);
                    continue;
                }

                try {
                    ResponseEntity<Boolean> response = userDriverClient.isDriverOnlineInternal(driverId);
                    boolean isOnline = Boolean.TRUE.equals(response.getBody());

                    if (!isOnline) {
                        continue;
                    }
                } catch (Exception e) {
                    log.error("Không thể check trạng thái tài xế {}, tạm thời bỏ qua để an toàn.", driverId);
                    continue;
                }

                Boolean isLockAcquired = redisTemplate.opsForValue().setIfAbsent(
                        "drivers:reserved:" + driverId,
                        bookingId.toString(),
                        30,
                        TimeUnit.SECONDS
                );

                if (Boolean.TRUE.equals(isLockAcquired)) {
                    log.info("Auto-Reassign: Đã tìm được tài xế thay thế ID {} ở bán kính {} km cho cuốc #{}",
                            driverId, radius, bookingId);

                    // Lưu reverse key để cancelBooking tra cứu nhanh
                    redisTemplate.opsForValue().set(
                            "booking:driver:" + bookingId, driverId.toString(), 20, TimeUnit.SECONDS
                    );

                    BookingResponse response = BookingResponse.builder()
                            .bookingId(currentBooking.getId())
                            .customerId(currentBooking.getCustomerId())
                            .driverId(driverId)
                            .startLongitude(currentBooking.getStartLongitude())
                            .startLatitude(currentBooking.getStartLatitude())
                            .endLongitude(currentBooking.getEndLongitude())
                            .endLatitude(currentBooking.getEndLatitude())
                            .status(currentBooking.getStatus())
                            .distanceInKm(distanceInKm)
                            .price(currentBooking.getPrice())
                            .build();

                    // Bắn WebSocket mời tài xế mới nhận cuốc
                    messagingTemplate.convertAndSendToUser(driverId.toString(), "/queue/driver/match", response);

                    // Kích hoạt lại bộ đếm ngược 20s cho tài xế mới
                    bookingTimeoutService.checkTimeoutAndCancel(bookingId, driverId);
                    return;
                }
            }
            log.info("Auto-Reassign cuốc #{}: Không thấy ai rảnh bán kính {} km, tiếp tục mở rộng...", bookingId, radius);
        }

        // Nếu quét đến 8km mà vẫn không có ai thay thế, lúc này mới chính thức HỦY cuốc
        log.warn("Auto-Reassign cuốc #{}: Đạt giới hạn 8km không tìm được ai thay thế. Huỷ chuyến cước.", bookingId);
        Booking finalBooking = bookingRepository.findById(bookingId).orElse(null);
        if (finalBooking != null && finalBooking.getStatus() == BookingStatus.PENDING) {
            finalBooking.setStatus(BookingStatus.CANCELLED);
            bookingRepository.save(finalBooking);

            BookingResponse cancelResponse = BookingResponse.builder()
                    .bookingId(finalBooking.getId())
                    .status(finalBooking.getStatus())
                    .build();

            // Báo về cho khách hàng biết là chịu chết, không tìm được xe
            messagingTemplate.convertAndSendToUser(finalBooking.getCustomerId().toString(), "/queue/booking/status", cancelResponse);
        }
    }
}