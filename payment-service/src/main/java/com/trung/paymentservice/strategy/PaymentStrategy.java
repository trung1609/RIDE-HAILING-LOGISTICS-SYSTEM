package com.trung.paymentservice.strategy;

import com.trung.paymentservice.dto.response.PaymentUrlResponse;

import java.math.BigDecimal;

public interface PaymentStrategy {
    PaymentUrlResponse createPayment(Long userId, Long driverId, Long bookingId, BigDecimal amount, String type);

    String getPaymentMethod();
}
