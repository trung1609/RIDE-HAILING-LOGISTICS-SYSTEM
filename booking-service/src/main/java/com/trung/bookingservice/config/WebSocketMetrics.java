package com.trung.bookingservice.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class WebSocketMetrics {

    private final AtomicInteger customerConnections = new AtomicInteger(0);
    private final AtomicInteger driverConnections = new AtomicInteger(0);

    public WebSocketMetrics(MeterRegistry meterRegistry) {
        Gauge.builder("active_users", customerConnections, AtomicInteger::get)
                .tag("role", "CUSTOMER")
                .description("Số lượng khách hàng online real-time")
                .register(meterRegistry);

        Gauge.builder("active_users", driverConnections, AtomicInteger::get)
                .tag("role", "DRIVER")
                .description("Số lượng tài xế online real-time")
                .register(meterRegistry);
    }

    @EventListener
    public void handleSessionConnectEvent(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String role = accessor.getFirstNativeHeader("role");

        if (role != null) {
            accessor.getSessionAttributes().put("role", role);
            if (role.equalsIgnoreCase("CUSTOMER")) {
                int current = customerConnections.incrementAndGet();
                log.info("📈 Khách hàng vừa online. Tổng KH: {}", current);
            } else if (role.equalsIgnoreCase("DRIVER")) {
                int current = driverConnections.incrementAndGet();
                log.info("📈 Tài xế vừa online. Tổng TX: {}", current);
            }
        }
    }

    @EventListener
    public void handleSessionDisconnectEvent(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();

        if (sessionAttributes != null && sessionAttributes.containsKey("role")) {
            String role = (String) sessionAttributes.get("role");
            if (role.equalsIgnoreCase("CUSTOMER")) {
                int current = customerConnections.decrementAndGet();
                if (current < 0) customerConnections.set(0);
                log.info("📉 Khách hàng vừa thoát. Tổng KH: {}", customerConnections.get());
            } else if (role.equalsIgnoreCase("DRIVER")) {
                int current = driverConnections.decrementAndGet();
                if (current < 0) driverConnections.set(0);
                log.info("📉 Tài xế vừa thoát. Tổng TX: {}", driverConnections.get());
            }
        }
    }
}