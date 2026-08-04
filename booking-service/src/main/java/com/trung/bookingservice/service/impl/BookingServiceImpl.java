package com.trung.bookingservice.service.impl;

import com.trung.bookingservice.exception.BadRequestException;
import com.trung.bookingservice.exception.ResourceNotFoundException;
import com.trung.bookingservice.service.BookingService;
import com.trung.bookingservice.service.client.LocationClient;
import com.trung.bookingservice.dto.request.BookingRequest;
import com.trung.bookingservice.dto.response.BookingResponse;
import com.trung.bookingservice.dto.response.DriverNearbyResponse;
import com.trung.bookingservice.entity.Booking;
import com.trung.bookingservice.repository.BookingRepository;
import com.trung.bookingservice.service.client.UserDriverClient;
import com.trung.bookingservice.util.LocationUtils;
import com.trung.bookingservice.util.enums.BookingStatus;
import com.trung.bookingservice.util.enums.DriverStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final LocationClient locationClient;
    private final SimpMessagingTemplate messagingTemplate;
    private final StringRedisTemplate redisTemplate;

    private static final double PRICE_PER_KM = 20000.0; // Cấu hình 20k/km
    private final UserDriverClient userDriverClient;
    private final BookingTimeoutService bookingTimeoutService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookingResponse createBooking(Long customerId, BookingRequest request) throws BadRequestException {
        boolean hasActiveBooking = bookingRepository.existsByCustomerIdAndStatusIn(
                customerId,
                List.of(
                        BookingStatus.PENDING,
                        BookingStatus.ACCEPTED,
                        BookingStatus.ARRIVED,
                        BookingStatus.IN_PROGRESS
                )
        );

        if (hasActiveBooking) {
            throw new BadRequestException("Bạn đang có một chuyến đi chưa hoàn thành. Không thể đặt thêm xe lúc này!");
        }
        // 1. Tính toán động khoảng cách chuyến đi và giá tiền
        double distance = LocationUtils.calculateDistance(
                request.getStartLatitude(), request.getStartLongitude(),
                request.getEndLatitude(), request.getEndLongitude()
        );
        double rawPrice = distance * PRICE_PER_KM;
        double calculatedPrice = (double) Math.round(rawPrice);

        // 2. Lưu thông tin thực thể chuyến đi vào DB
        Booking booking = Booking.builder()
                .customerId(customerId)
                .startLongitude(request.getStartLongitude())
                .startLatitude(request.getStartLatitude())
                .endLongitude(request.getEndLongitude())
                .endLatitude(request.getEndLatitude())
                .status(BookingStatus.PENDING)
                .price(calculatedPrice)
                .createdAt(LocalDateTime.now())
                .build();
        bookingRepository.save(booking);
        bookingRepository.flush();

        log.info("Khách hàng {} đặt xe. Quãng đường: {} km. Thành tiền: {} VND", customerId, String.format("%.2f", distance), calculatedPrice);

        double[] searchRadiuses = {3.0, 5.0, 8.0};

        for (double radius : searchRadiuses) {
            List<DriverNearbyResponse> nearbyDrivers = locationClient.getNearbyDrivers(
                    request.getStartLongitude(),
                    request.getStartLatitude(),
                    radius
            );

            for (DriverNearbyResponse driver : nearbyDrivers) {
                Long driverId = driver.getDriverId();

                Boolean isLockAcquired = redisTemplate.opsForValue().setIfAbsent(
                        "drivers:reserved:" + driverId,
                        booking.getId().toString(),
                        20,
                        TimeUnit.SECONDS
                );

                if (Boolean.TRUE.equals(isLockAcquired)) {
                    log.info("Đã chiếm khóa thành công tài xế ID {} ở bán kính {} km (cách điểm đón {} km)",
                            driverId, radius, String.format("%.2f", driver.getDistanceInKm()));

                    BookingResponse response = convertToResponse(booking, distance);

                    // Bắn WebSocket mời nhận cuốc
                    messagingTemplate.convertAndSendToUser(
                            driverId.toString(),
                            "/queue/driver/match",
                            response
                    );

                    // Gọi tiến trình ngầm đếm ngược 20s
                    bookingTimeoutService.checkTimeoutAndCancel(booking.getId(), driverId);

                    return response;
                } else {
                    log.warn("Tài xế {} vừa bị khách khác giữ chỗ, đang thử tài xế tiếp theo...", driverId);
                }
            }

            log.info("Khách hàng {}: Không có tài xế rảnh trong bán kính {} km, đang mở rộng bán kính...", customerId, radius);
        }

        // Nếu quét hết các nấc bán kính mà vẫn không giành được tài xế nào
        log.warn("Đã mở rộng đến bán kính tối đa 8km nhưng không có tài xế nào rảnh.");
        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
        return convertToResponse(booking, distance);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookingResponse acceptBooking(Long driverId, Long bookingId) throws BadRequestException {
        log.info("Tài xế {} đang yêu cầu nhận chuyến đi có ID = {}", driverId, bookingId);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy chuyến đi."));

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BadRequestException("Chuyến đi không hợp lệ để chấp nhận. Trạng thái hiện tại: " + booking.getStatus());
        }


        booking.setStatus(BookingStatus.ACCEPTED);
        booking.setDriverId(driverId);
        booking.setAcceptedAt(LocalDateTime.now());
        bookingRepository.save(booking);

        log.info("Tài xế {} đã chấp nhận chuyến đi {}", driverId, bookingId);

        redisTemplate.delete("drivers:reserved:" + driverId);
        userDriverClient.updateDriverStatusInternal(driverId, DriverStatus.BUSY);
        log.info("Tài xế {} đã được cập nhật trạng thái sang BUSY.", driverId);

        // 3. Đóng gói response và bắn WebSocket báo cho Khách hàng biết
        BookingResponse response = convertToResponse(booking, LocationUtils.calculateDistance(
                booking.getStartLatitude(), booking.getStartLongitude(),
                booking.getEndLatitude(), booking.getEndLongitude()
        ));

        messagingTemplate.convertAndSendToUser(
                booking.getCustomerId().toString(),
                "/queue/booking/status",
                response
        );

        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookingResponse updateBookingStatus(Long driverId, Long bookingId, BookingStatus newStatus) throws ResourceNotFoundException, BadRequestException {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyến đi."));

        if (!driverId.equals(booking.getDriverId())) {
            throw new BadRequestException("Tài xế không có quyền thao tác trên chuyến đi này.");
        }

        booking.setStatus(newStatus);
        bookingRepository.save(booking);

        log.info("Chuyến đi {} đã chuyển sang trạng thái: {}", bookingId, newStatus);

        BookingResponse response = convertToResponse(booking, LocationUtils.calculateDistance(
                booking.getStartLatitude(), booking.getStartLongitude(),
                booking.getEndLatitude(), booking.getEndLongitude()
        ));

        messagingTemplate.convertAndSendToUser(
                booking.getCustomerId().toString(),
                "/queue/booking/status",
                response
        );

        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookingResponse completeTrip(Long driverId, Long bookingId) throws ResourceNotFoundException, BadRequestException {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyến đi."));

        if (!driverId.equals(booking.getDriverId())) {
            throw new BadRequestException("Tài xế không có quyền hoàn thành chuyến đi này.");
        }

        booking.setStatus(BookingStatus.COMPLETED);
        booking.setCompletedAt(LocalDateTime.now());
        bookingRepository.save(booking);

        log.info("Chuyến đi {} đã HOÀN THÀNH.", bookingId);
        userDriverClient.updateDriverStatusInternal(driverId, DriverStatus.ONLINE);

        BookingResponse response = convertToResponse(booking, LocationUtils.calculateDistance(
                booking.getStartLatitude(), booking.getStartLongitude(),
                booking.getEndLatitude(), booking.getEndLongitude()
        ));

        messagingTemplate.convertAndSendToUser(
                booking.getCustomerId().toString(),
                "/queue/booking/status",
                response
        );

        return response;
    }

    private BookingResponse convertToResponse(Booking booking, double distance) {
        return BookingResponse.builder()
                .bookingId(booking.getId())
                .customerId(booking.getCustomerId())
                .driverId(booking.getDriverId())
                .startLongitude(booking.getStartLongitude())
                .startLatitude(booking.getStartLatitude())
                .endLongitude(booking.getEndLongitude())
                .endLatitude(booking.getEndLatitude())
                .status(booking.getStatus())
                .distanceInKm(distance)
                .price(booking.getPrice())
                .build();
    }
}