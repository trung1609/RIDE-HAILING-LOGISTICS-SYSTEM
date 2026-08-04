package com.trung.locationservice.config;

import com.trung.locationservice.service.client.UserDriverClient;
import com.trung.locationservice.service.LocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final LocationService locationService;
    private final UserDriverClient userDriverClient;

    // Bản đồ lưu trữ tạm thời mapping giữa SessionId của WebSocket và DriverId
    private final Map<String, Long> sessionDriverMap = new ConcurrentHashMap<>();

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        // Đọc driverId từ header lúc client CONNECT
        String driverIdStr = headerAccessor.getFirstNativeHeader("driverId");
        if (driverIdStr != null) {
            Long driverId = Long.parseLong(driverIdStr);
            String sessionId = headerAccessor.getSessionId();
            sessionDriverMap.put(sessionId, driverId);
            log.info("Tài xế {} đã thiết lập kết nối WebSocket. Session: {}", driverId, sessionId);
            try {
                userDriverClient.setDriverStatusInternal(driverId, true);
                log.info("Đã tự động cập nhật trạng thái ONLINE trong DB cho tài xế {}", driverId);
            } catch (Exception e) {
                log.error("Không thể đồng bộ trạng thái ONLINE về user-driver-service: {}", e.getMessage());
            }
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        Long driverId = sessionDriverMap.remove(sessionId);
        if (driverId != null) {
            log.warn("Phát hiện kết nối bị ngắt từ tài xế {} (Session: {}). Tiến hành tự động dọn dẹp...", driverId, sessionId);

            // 1. Tự động xóa khỏi bản đồ RAM Redis
            locationService.removeDriverLocation(driverId);

            // 2. Tự động gọi Feign cập nhật DB thành OFFLINE
            try {
                userDriverClient.setDriverStatusInternal(driverId, false);
                log.info("Đã tự động cập nhật trạng thái OFFLINE trong DB cho tài xế {}", driverId);
            } catch (Exception e) {
                log.error("Không thể đồng bộ trạng thái OFFLINE về user-driver-service: {}", e.getMessage());
            }
        }
    }
}