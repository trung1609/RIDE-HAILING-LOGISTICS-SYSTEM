package com.trung.bookingservice.service.impl;

import com.trung.bookingservice.dto.response.BookingResponse;
import com.trung.bookingservice.entity.Booking;
import com.trung.bookingservice.repository.BookingRepository;
import com.trung.bookingservice.util.LocationUtils;
import com.trung.bookingservice.util.enums.BookingStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingTimeoutService {

    private final BookingRepository bookingRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final StringRedisTemplate redisTemplate;

    @Lazy
    private final BookingReassignService bookingReassignService;

    @Async
    @Transactional
    public void checkTimeoutAndCancel(Long bookingId, Long invitedDriverId) {
        try {
            log.info("Bắt đầu đếm ngược 20s cho tài xế ID: {} với cuốc xe #{}", invitedDriverId, bookingId);
            Thread.sleep(20000);

            String redisReservedBookingId = redisTemplate.opsForValue().get("drivers:reserved:" + invitedDriverId);

            if (redisReservedBookingId == null || !redisReservedBookingId.equals(bookingId.toString())) {
                log.info("Phiên đếm ngược cũ của tài xế {} cho cuốc #{} đã hết hiệu lực (Tài xế đã thao tác hoặc cuốc đã chuyển vị trí).", invitedDriverId, bookingId);
                return;
            }

            // Đọc trạng thái mới nhất từ Database
            Booking booking = bookingRepository.findById(bookingId).orElse(null);

            if (booking != null && booking.getStatus() == BookingStatus.PENDING) {
                log.warn("Tài xế ID {} quá 20s không nhận cuốc #{}. Tiến hành giải phóng và kiểm tra luồng.", invitedDriverId, bookingId);

                // Giải phóng tài xế này trên Redis
                redisTemplate.delete("drivers:reserved:" + invitedDriverId);
                redisTemplate.delete("bookings:driver:" + bookingId);

                String rejectedKey = "booking:rejected:" + bookingId;
                redisTemplate.opsForSet().add(rejectedKey, invitedDriverId.toString());
                redisTemplate.expire(rejectedKey, 5, TimeUnit.MINUTES);

                BookingResponse response = BookingResponse.builder()
                        .bookingId(booking.getId())
                        .status(BookingStatus.CANCELLED)
                        .build();

                // Bắn WebSocket báo cho chính tài xế bị hụt cuốc ẩn hộp thoại đi
                messagingTemplate.convertAndSendToUser(invitedDriverId.toString(), "/queue/driver/match", response);

                double distance = LocationUtils.calculateDistance(
                        booking.getStartLatitude(), booking.getStartLongitude(),
                        booking.getEndLatitude(), booking.getEndLongitude()
                );
                bookingReassignService.reassignNewDriverAsync(
                        bookingId, booking.getStartLongitude(), booking.getStartLatitude(), distance
                );

                log.info("[Timeout-Done] Đã dọn dẹp xong phiên hết hạn của tài xế {}", invitedDriverId);
            }

        } catch (InterruptedException e) {
            log.error("Tiến trình đếm ngược Timeout bị gián đoạn", e);
            Thread.currentThread().interrupt();
        }
    }
}