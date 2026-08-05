package com.trung.bookingservice.service.impl;

import com.trung.bookingservice.dto.request.PricingRequest;
import com.trung.bookingservice.dto.response.PricingResponse;
import com.trung.bookingservice.exception.BadRequestException;
import com.trung.bookingservice.exception.ResourceNotFoundException;
import com.trung.bookingservice.service.BookingService;
import com.trung.bookingservice.service.client.LocationClient;
import com.trung.bookingservice.dto.request.BookingRequest;
import com.trung.bookingservice.dto.response.BookingResponse;
import com.trung.bookingservice.dto.response.DriverNearbyResponse;
import com.trung.bookingservice.entity.Booking;
import com.trung.bookingservice.repository.BookingRepository;
import com.trung.bookingservice.service.client.PricingClient;
import com.trung.bookingservice.service.client.UserDriverClient;
import com.trung.bookingservice.util.LocationUtils;
import com.trung.bookingservice.util.enums.BookingStatus;
import com.trung.bookingservice.util.enums.DriverStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    private final PricingClient pricingClient;
    private final BookingReassignService bookingReassignService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookingResponse createBooking(Long customerId, BookingRequest request) throws BadRequestException {

        String spamKey = "customer:spam:" + customerId;
        Boolean isSpamming = redisTemplate.hasKey(spamKey);

        if (Boolean.TRUE.equals(isSpamming)) {
            log.warn("Khách hàng {} đang thao tác quá nhanh, chặn spam!", customerId);
            throw new BadRequestException("Hệ thống đang xử lý yêu cầu trước đó. Vui lòng thử lại sau 5 giây!");
        }

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

        redisTemplate.opsForValue().set(spamKey, "locked", 5, TimeUnit.SECONDS);

        // Gọi location service MỘT LẦN với bán kính lớn nhất
        List<DriverNearbyResponse> allNearbyDrivers = locationClient.getNearbyDrivers(
                request.getStartLongitude(),
                request.getStartLatitude(),
                8.0
        );

        // Đếm tài xế trong bán kính 5km cho pricing (filter in-memory)
        int supplyCount = (int) allNearbyDrivers.stream()
                .filter(d -> d.getDistanceInKm() <= 5.0)
                .count();

        PricingRequest pricingRequest = PricingRequest.builder()
                .startLongitude(request.getStartLongitude())
                .startLatitude(request.getStartLatitude())
                .endLongitude(request.getEndLongitude())
                .endLatitude(request.getEndLatitude())
                .build();

        PricingResponse pricingInfo;
        try {
            pricingInfo = pricingClient.calculatePrice(pricingRequest, supplyCount);
        } catch (Exception e) {
            log.error("Lỗi khi gọi pricing-service, tự động áp dụng giá cơ sở fallback. Chi tiết: {}", e.getMessage());
            double distanceFallback = LocationUtils.calculateDistance(
                    request.getStartLatitude(), request.getStartLongitude(),
                    request.getEndLatitude(), request.getEndLongitude()
            );
            pricingInfo = new PricingResponse();
            pricingInfo.setTotalPrice(Math.round(distanceFallback * 20000.0));
            pricingInfo.setDistanceInKm(distanceFallback);
            pricingInfo.setSurgeMultiplier(1.0);
        }

        Booking booking = Booking.builder()
                .customerId(customerId)
                .startLongitude(request.getStartLongitude())
                .startLatitude(request.getStartLatitude())
                .endLongitude(request.getEndLongitude())
                .endLatitude(request.getEndLatitude())
                .status(BookingStatus.PENDING)
                .price(pricingInfo.getTotalPrice())
                .createdAt(LocalDateTime.now())
                .build();
        bookingRepository.save(booking);
        bookingRepository.flush();

        log.info("Khách hàng {} đặt xe. Quãng đường: {} km. Surge Multiplier: {}x. Thành tiền: {} VND",
                customerId, String.format("%.2f", pricingInfo.getDistanceInKm()), pricingInfo.getSurgeMultiplier(), pricingInfo.getTotalPrice());

        double[] searchRadiuses = {3.0, 5.0, 8.0};

        for (double radius : searchRadiuses) {
            // Filter in-memory theo từng nấc bán kính, không gọi thêm Feign
            List<DriverNearbyResponse> nearbyDrivers = allNearbyDrivers.stream()
                    .filter(d -> d.getDistanceInKm() <= radius)
                    .toList();

            for (DriverNearbyResponse driver : nearbyDrivers) {
                Long driverId = driver.getDriverId();

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
                        booking.getId().toString(),
                        20,
                        TimeUnit.SECONDS
                );

                if (Boolean.TRUE.equals(isLockAcquired)) {
                    log.info("Đã chiếm khóa thành công tài xế ID {} ở bán kính {} km (cách điểm đón {} km)",
                            driverId, radius, String.format("%.2f", driver.getDistanceInKm()));

                    // Lưu reverse key để cancelBooking tra cứu nhanh thay vì gọi location service
                    redisTemplate.opsForValue().set(
                            "booking:driver:" + booking.getId(), driverId.toString(), 20, TimeUnit.SECONDS
                    );

                    BookingResponse response = convertToResponse(booking, pricingInfo.getDistanceInKm());

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
        return convertToResponse(booking, pricingInfo.getDistanceInKm());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookingResponse cancelBooking(Long customerId, Long bookingId) throws ResourceNotFoundException, BadRequestException {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyến đi."));

        if (!customerId.equals(booking.getCustomerId())) {
            throw new BadRequestException("Bạn không có quyền thao tác trên chuyến đi này.");
        }

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BadRequestException("Không thể hủy chuyến đi này. Trạng thái hiện tại: " + booking.getStatus());
        }

        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        log.info("Khách hàng {} đã chủ động hủy chuyến đi #{}", customerId, bookingId);

        // Tra cứu driver đang được giữ chỗ qua Redis reverse key, không gọi Feign
        String reservedDriverIdStr = redisTemplate.opsForValue().get("booking:driver:" + bookingId);
        if (reservedDriverIdStr != null) {
            Long invitedDriverId = Long.parseLong(reservedDriverIdStr);
            redisTemplate.delete("drivers:reserved:" + invitedDriverId);
            redisTemplate.delete("booking:driver:" + bookingId);

            // Bắn WebSocket báo cho tài xế ẩn hộp thoại
            BookingResponse cancelResponse = convertToResponse(booking, 0.0);
            messagingTemplate.convertAndSendToUser(
                    invitedDriverId.toString(),
                    "/queue/driver/match",
                    cancelResponse
            );
        }

        BookingResponse response = convertToResponse(booking, LocationUtils.calculateDistance(
                booking.getStartLatitude(), booking.getStartLongitude(),
                booking.getEndLatitude(), booking.getEndLongitude()
        ));

        // Bắn WebSocket báo về cho chính Khách hàng để cập nhật giao diện
        messagingTemplate.convertAndSendToUser(
                booking.getCustomerId().toString(),
                "/queue/booking/status",
                response
        );

        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookingResponse acceptBooking(Long driverId, Long bookingId) throws BadRequestException {
        log.info("Tài xế {} đang yêu cầu nhận chuyến đi có ID = {}", driverId, bookingId);
        String reservedBookingId = redisTemplate.opsForValue().get("drivers:reserved:" + driverId);

        if (reservedBookingId == null || !reservedBookingId.equals(bookingId.toString())) {
            log.error("CẢNH BÁO BẢO MẬT: Tài xế {} cố tình nhận cuốc xe #{} không được phân công cho mình!", driverId, bookingId);
            throw new BadRequestException("Thao tác thất bại: Cuốc xe này không dành cho bạn, hoặc đã bị khách hàng hủy/hết hạn!");
        }
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
    public BookingResponse cancelBookingByDriver(Long driverId, Long bookingId) throws ResourceNotFoundException, BadRequestException {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyến đi."));

        if (!driverId.equals(booking.getDriverId())) {
            throw new BadRequestException("Bạn không có quyền hủy chuyến đi này.");
        }

        if (booking.getStatus() != BookingStatus.ACCEPTED && booking.getStatus() != BookingStatus.ARRIVED) {
            throw new BadRequestException("Không thể hủy chuyến đi ở trạng thái hiện tại: " + booking.getStatus());
        }

        double distance = LocationUtils.calculateDistance(
                booking.getStartLatitude(), booking.getStartLongitude(),
                booking.getEndLatitude(), booking.getEndLongitude()
        );
        booking.setStatus(BookingStatus.PENDING);
        booking.setDriverId(null);
        bookingRepository.save(booking);
        bookingRepository.flush();

        log.info("Tài xế {} hủy chuyến #{}. Hệ thống đưa trạng thái về PENDING để tìm người thay thế.", driverId, bookingId);

        String rejectedKey = "booking:rejected:" + bookingId;
        redisTemplate.opsForSet().add(rejectedKey, driverId.toString());
        redisTemplate.expire(rejectedKey, 30, TimeUnit.MINUTES);

        userDriverClient.updateDriverStatusInternal(driverId, DriverStatus.ONLINE);
        redisTemplate.delete("drivers:reserved:" + driverId);

        BookingResponse response = convertToResponse(booking, distance);

        BookingResponse driverCancelResponse = BookingResponse.builder()
                .bookingId(booking.getId())
                .customerId(booking.getCustomerId())
                .driverId(driverId)
                .startLongitude(booking.getStartLongitude())
                .startLatitude(booking.getStartLatitude())
                .endLongitude(booking.getEndLongitude())
                .endLatitude(booking.getEndLatitude())
                .distanceInKm(distance)
                .price(booking.getPrice())
                .status(BookingStatus.CANCELLED)
                .build();

        // chi bắn WebSocket sau khi transaction commit để tránh trường hợp rollback
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronizationAdapter() {
                    @Override
                    public void afterCommit() {
                        // Bắn WebSocket báo tài xế ẩn hộp thoại
                        messagingTemplate.convertAndSendToUser(driverId.toString(), "/queue/driver/match", driverCancelResponse);

                        // Bắn WebSocket thông báo trạng thái PENDING mới cho Khách hàng
                        messagingTemplate.convertAndSendToUser(booking.getCustomerId().toString(), "/queue/booking/status", response);

                        // KÍCH HOẠT TIẾN TRÌNH NGẦM AUTO-REASSIGN TÌM TÀI XẾ KHÁC
                        bookingReassignService.reassignNewDriverAsync(
                                booking.getId(),
                                booking.getStartLongitude(),
                                booking.getStartLatitude(),
                                distance
                        );
                    }
                }
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